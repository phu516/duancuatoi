package com.example.demo.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.*;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.core.StreamReadFeature;

@Configuration
public class JsonConfig {
    @Bean JsonMapperBuilderCustomizer apiJson() {
        return builder -> {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
            builder.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
            builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
            builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
            builder.withCoercionConfig(LogicalType.Textual, c -> c
                .setCoercion(CoercionInputShape.Integer,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float,CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail));
        };
    }
}
