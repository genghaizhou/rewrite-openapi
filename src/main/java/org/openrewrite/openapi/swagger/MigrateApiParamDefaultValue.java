/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.openapi.swagger;

import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;
import java.util.stream.Collectors;

public class MigrateApiParamDefaultValue extends Recipe {
    private static final String FQN_SCHEMA = "io.swagger.v3.oas.annotations.media.Schema";
    private static final AnnotationMatcher PARAMETER_ANNOTATION_MATCHER = new AnnotationMatcher("io.swagger.v3.oas.annotations.Parameter");

    @Override
    public String getDisplayName() {
        return "Migrate `@ApiParam(defaultValue)` to `@Parameter(schema)`";
    }

    @Override
    public String getDescription() {
        return "Migrate `@ApiParam(defaultValue)` to `@Parameter(schema = @Schema(defaultValue))`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // This recipe is after ChangeType recipe
        return Preconditions.check(
                new UsesMethod<>("io.swagger.annotations.ApiParam defaultValue()", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation anno = super.visitAnnotation(annotation, ctx);

                        if (!PARAMETER_ANNOTATION_MATCHER.matches(anno)) {
                            return anno;
                        }

                        StringJoiner tpl = new StringJoiner(", ");
                        StringJoiner schemaTpl = new StringJoiner(", ", "schema = @Schema(", ")");
                        List<Expression> tplArgs = new ArrayList<>();
                        List<Expression> schemaTplArgs = new ArrayList<>();

                        for (Expression attr : anno.getArguments()) {
                            if (isDefaultValue(attr)) { // handle defaultValue
                                Expression assignment = ((J.Assignment) attr).getAssignment();
                                schemaTpl.add("defaultValue = #{any()}");
                                schemaTplArgs.add(assignment);
                            } else if (isAllowableValues(attr)) { // handle allowableValues
                                Expression attrValue = ((J.Assignment) attr).getAssignment();

                                String arrayValues = Arrays.stream(attrValue.toString().split(","))
                                        .map(v -> "\"" + v.trim() + "\"")
                                        .collect(Collectors.joining(", ", "{", "}"));

                                // TODO attr 属性值改为 arrayValues
                                // 创建数组表达式来替换原始的字符串值
                                anno = JavaTemplate.builder(arrayValues)
                                        .build()
                                        .apply(updateCursor(anno), attrValue.getCoordinates().replace());

                                schemaTpl.add("allowableValues = #{any()}");
                                schemaTplArgs.add(findAllowableValues(anno));
                            } else {
                                tpl.add("#{any()}");
                                tplArgs.add(attr);
                            }
                        }

                        //String javaTpl = schemaTpl.length() > 18 ? tpl.add(schemaTpl.toString()).toString() : tpl.toString();
                        String javaTpl = tpl.toString();
                        if (!schemaTplArgs.isEmpty()) {
                            javaTpl = tpl.add(schemaTpl.toString()).toString();
                            tplArgs.addAll(schemaTplArgs);
                        }

                        anno = JavaTemplate.builder(javaTpl)
                                .imports(FQN_SCHEMA)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-annotations"))
                                .build()
                                .apply(updateCursor(anno), annotation.getCoordinates().replaceArguments(), tplArgs.toArray());
                        maybeAddImport(FQN_SCHEMA, false);
                        return maybeAutoFormat(annotation, anno, ctx, getCursor().getParentTreeCursor());
                    }

                    private boolean isDefaultValue(Expression exp) {
                        return exp instanceof J.Assignment && "defaultValue".equals(((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName());
                    }

                    private boolean isAllowableValues(Expression exp) {
                        return exp instanceof J.Assignment && "allowableValues".equals(((J.Identifier) ((J.Assignment) exp).getVariable()).getSimpleName());
                    }

                    private Expression findAllowableValues(Expression anno) {
                        if (anno instanceof J.Annotation) {
                            return ((J.Annotation) anno).getArguments().stream()
                                    .filter(this::isAllowableValues)
                                    .map(attr -> ((J.Assignment) attr).getAssignment())
                                    .findFirst()
                                    .orElse(null);
                        }
                        return null;
                    }
                }
        );
    }
}
