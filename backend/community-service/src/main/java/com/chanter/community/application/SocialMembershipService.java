package com.chanter.community.application;

import com.chanter.community.domain.CoMember;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SocialMembershipService {

    private final StudyServerRepository studyServerRepository;

    public SocialMembershipService(StudyServerRepository studyServerRepository) {
        this.studyServerRepository = studyServerRepository;
    }

    public boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId) {
        if (firstUserId.equals(secondUserId)) {
            return false;
        }

        return studyServerRepository.shareStudyServerMembership(firstUserId, secondUserId);
    }

    public List<CoMember> findCoMembers(UUID viewerUserId) {
        return studyServerRepository.findCoMembers(viewerUserId);
    }
}
