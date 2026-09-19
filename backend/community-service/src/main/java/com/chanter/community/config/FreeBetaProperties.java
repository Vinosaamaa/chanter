package com.chanter.community.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chanter.beta")
public record FreeBetaProperties(
        @DefaultValue("free_beta") @Pattern(regexp = "free_beta") String mode,
        @DefaultValue("1000") @Min(1) @Max(1000) int assistantRunLimit
) {
}
