package com.chanter.auth.moderation;

import com.chanter.common.auth.ModerationDirectoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlatformDirectoryController {
    private final ModerationDirectory directory;
    public PlatformDirectoryController(ModerationDirectory directory) { this.directory=directory; }
    @GetMapping("/api/v1/platform-admin/directory")
    List<ModerationDirectoryItem> search(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@RequestParam String type,
            @RequestParam String query,@RequestParam(defaultValue="0") int offset,@RequestParam String reason,
            @RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return directory.search(authorization,verification,type,query,offset,reason,correlation);
    }
}
