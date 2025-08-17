package org.openrewrite.openapi.swagger;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * @author: Hardy
 * @since: 2025/8/15 17:58
 * @description: - 略
 */
public class MigrateApiParamToParameterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.openapi.swagger.MigrateApiParamToParameter")
          .parser(JavaParser.fromJavaVersion().classpath("swagger-annotations-1.+", "swagger-annotations-2.+"));
    }

    @DocumentExample
    @Test
    void convert() {
        //language=java
        rewriteRun(
          java("""
             package com.openrewrite.test.model;

             import io.swagger.annotations.ApiParam;

             public class SkuQueryRequest {
                 @ApiParam(example = "1001", required = true, value = "PID")
                 private Long poiId;

                 @ApiParam(example = "0", required = true, defaultValue = "30", allowableValues="10,20", value = "分页offset")
                 private Integer offset = 0;

                 @ApiParam(example = "0", required = true, defaultValue = "0", value = "不限区间 -1 其他 1 默认1")
                 private Integer notLimit;

                 @ApiParam(value = "是否仅搜索,false:否，true:是", defaultValue = "false", hidden = true)
                 private Boolean isSkuOnly = false;
             }
            """,
            """
             package com.openrewrite.test.model;

             import io.swagger.v3.oas.annotations.Parameter;
             import io.swagger.v3.oas.annotations.media.Schema;

             public class SkuQueryRequest {
                 @Parameter(example = "1001", required = true, description = "PID")
                 private Long poiId;

                 @Parameter(example = "0", required = true, description = "分页offset", schema = @Schema(defaultValue = "30", allowableValues = {"10", "20"}))
                 private Integer offset = 0;

                 @Parameter(example = "0", required = true, description = "不限区间 -1 其他 1 默认1", schema = @Schema(defaultValue = "0"))
                 private Integer notLimit;

                 @Parameter(description = "是否仅搜索,false:否，true:是", hidden = true, schema = @Schema(defaultValue = "false"))
                 private Boolean isSkuOnly = false;
             }
            """
            ));
    }
}
