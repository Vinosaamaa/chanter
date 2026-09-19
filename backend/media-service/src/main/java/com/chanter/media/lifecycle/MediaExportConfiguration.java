package com.chanter.media.lifecycle;

import com.chanter.common.lifecycle.ExportSourceConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(ExportSourceConfiguration.class)
public class MediaExportConfiguration { }
