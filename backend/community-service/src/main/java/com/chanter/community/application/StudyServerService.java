package com.chanter.community.application;

import com.chanter.community.api.UserStudyServerInvitationResponse;
import com.chanter.community.domain.AuthUserProfile;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.OwnerRole;
import com.chanter.community.domain.SaasPlanTier;
import com.chanter.community.domain.StudyServer;
import com.chanter.community.domain.StudyServerChannel;
import com.chanter.community.domain.StudyServerInvitation;
import com.chanter.community.domain.StudyServerInvitationStatus;
import com.chanter.community.domain.StudyServerMember;
import com.chanter.community.domain.StudyServerRole;
import com.chanter.community.domain.StudyServerType;
import com.chanter.community.domain.TextChannelMessageAccess;
import com.chanter.community.domain.VoiceMediaToken;
import com.chanter.community.domain.VoicePresence;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyServerService {

    private final StudyServerRepository repository;
    private final AuthUserDirectoryClient authUserDirectoryClient;
    private final LiveKitTokenIssuer liveKitTokenIssuer;
    private final Clock clock;

    public StudyServerService(
            StudyServerRepository repository,
            AuthUserDirectoryClient authUserDirectoryClient,
            LiveKitTokenIssuer liveKitTokenIssuer,
            Clock clock
    ) {
        this.repository = repository;
        this.authUserDirectoryClient = authUserDirectoryClient;
        this.liveKitTokenIssuer = liveKitTokenIssuer;
        this.clock = clock;
    }

    @Transactional
    public StudyServer createStudyServer(
            String name,
            String description,
            StudyServerType serverType,
            List<String> inviteEmails,
            UUID ownerUserId
    ) {
        List<ResolvedInvite> resolvedInvites = resolveInvitees(inviteEmails);

        UUID studyServerId = UUID.randomUUID();
        StudyServer studyServer = new StudyServer(
                studyServerId,
                name.trim(),
                description == null || description.isBlank() ? null : description.trim(),
                serverType,
                new OwnerRole(ownerUserId, StudyServerRole.STUDY_SERVER_OWNER),
                SaasPlanTier.STARTER,
                List.of(
                        new StudyServerChannel(UUID.randomUUID(), studyServerId, "announcements", ChannelKind.TEXT, 0),
                        new StudyServerChannel(UUID.randomUUID(), studyServerId, "general", ChannelKind.TEXT, 1),
                        new StudyServerChannel(UUID.randomUUID(), studyServerId, "study-room", ChannelKind.VOICE, 2)
                ),
                clock.instant()
        );

        repository.save(studyServer);

        for (ResolvedInvite invite : resolvedInvites) {
            repository.saveInvitation(new StudyServerInvitation(
                    UUID.randomUUID(),
                    studyServerId,
                    invite.userId(),
                    invite.email(),
                    ownerUserId,
                    StudyServerInvitationStatus.PENDING,
                    clock.instant(),
                    null
            ));
        }

        return studyServer;
    }

    private List<ResolvedInvite> resolveInvitees(List<String> inviteEmails) {
        if (inviteEmails == null || inviteEmails.isEmpty()) {
            return List.of();
        }

        List<ResolvedInvite> resolved = new ArrayList<>(inviteEmails.size());
        for (String email : inviteEmails) {
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite email must not be blank");
            }
            String normalizedEmail = normalizeEmail(email);
            AuthUserProfile invitedProfile = authUserDirectoryClient.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User email not found"));
            resolved.add(new ResolvedInvite(invitedProfile.userId(), normalizedEmail));
        }
        return resolved;
    }

    private record ResolvedInvite(UUID userId, String email) {
    }

    public List<StudyServerInvitation> findPendingInvitations(UUID studyServerId) {
        return repository.findPendingInvitations(studyServerId);
    }

    public List<UserStudyServerInvitationResponse> findPendingInvitationsForUser(UUID inviteeUserId) {
        return repository.findPendingInvitationsForUser(inviteeUserId).stream()
                .map(invitation -> {
                    String studyServerName = repository.findById(invitation.studyServerId())
                            .map(StudyServer::name)
                            .orElse("Study Server");
                    return UserStudyServerInvitationResponse.from(invitation, studyServerName);
                })
                .toList();
    }

    public void acceptStudyServerInvitation(UUID studyServerId, UUID invitationId, UUID inviteeUserId) {
        StudyServerInvitation invitation = repository.findInvitation(studyServerId, invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        if (!invitation.invitedUserId().equals(inviteeUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the invited user can accept this invitation");
        }
        if (invitation.status() != StudyServerInvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation is no longer pending");
        }
        repository.acceptInvitation(studyServerId, invitationId, inviteeUserId, clock.instant());
    }

    @Transactional
    public List<StudyServerInvitation> createInvitations(
            UUID studyServerId,
            UUID actorUserId,
            List<String> inviteEmails
    ) {
        StudyServer studyServer = repository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
        if (!studyServer.ownerRole().userId().equals(actorUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the Study Server owner can invite people"
            );
        }
        if (inviteEmails == null || inviteEmails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one invite email is required");
        }

        List<ResolvedInvite> resolvedInvites = resolveInvitees(inviteEmails);
        List<StudyServerInvitation> created = new ArrayList<>(resolvedInvites.size());
        Instant now = clock.instant();
        for (ResolvedInvite invite : resolvedInvites) {
            if (invite.userId().equals(actorUserId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot invite the Study Server owner");
            }
            if (repository.isStudyServerMember(studyServerId, invite.userId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "User is already a member of this Study Server"
                );
            }
            try {
                StudyServerInvitation invitation = repository.saveInvitation(new StudyServerInvitation(
                        UUID.randomUUID(),
                        studyServerId,
                        invite.userId(),
                        invite.email(),
                        actorUserId,
                        StudyServerInvitationStatus.PENDING,
                        now,
                        null
                ));
                created.add(invitation);
            } catch (DuplicateKeyException ex) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A pending invitation already exists for " + invite.email()
                );
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public StudyServerMemberDirectory listMembers(
            UUID studyServerId,
            UUID viewerUserId,
            String search,
            String filter,
            int limit,
            int offset
    ) {
        if (!repository.isStudyServerMember(studyServerId, viewerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Study Server membership required");
        }
        repository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));

        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        int effectiveOffset = Math.max(offset, 0);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedFilter = filter == null || filter.isBlank()
                ? "ALL"
                : filter.trim().toUpperCase(Locale.ROOT);

        List<StudyServerMember> members = repository.findMembers(studyServerId);
        Map<UUID, AuthUserProfile> profiles = new LinkedHashMap<>();
        List<UUID> userIds = members.stream().map(StudyServerMember::userId).toList();
        if (!userIds.isEmpty()) {
            authUserDirectoryClient.findByIds(userIds)
                    .forEach(profile -> profiles.put(profile.userId(), profile));
        }

        List<StudyServerMemberDetails> enriched = members.stream()
                .map(member -> {
                    AuthUserProfile profile = profiles.get(member.userId());
                    String displayName = profile == null || profile.displayName() == null || profile.displayName().isBlank()
                            ? "Member"
                            : profile.displayName();
                    String email = profile == null ? null : profile.email();
                    boolean staff = isStaffRole(member.role());
                    return new StudyServerMemberDetails(
                            member.userId(),
                            displayName,
                            email,
                            member.role(),
                            staff
                    );
                })
                .filter(member -> matchesFilter(member, normalizedFilter))
                .filter(member -> matchesSearch(member, normalizedSearch))
                .toList();

        int total = enriched.size();
        List<StudyServerMemberDetails> page = enriched.stream()
                .skip(effectiveOffset)
                .limit(effectiveLimit)
                .toList();
        return new StudyServerMemberDirectory(page, total, repository.countMembers(studyServerId));
    }

    @Transactional(readOnly = true)
    public StudyServerMemberSummary memberSummary(UUID studyServerId, UUID viewerUserId) {
        if (!repository.isStudyServerMember(studyServerId, viewerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Study Server membership required");
        }
        repository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));

        List<StudyServerMember> members = repository.findMembers(studyServerId);
        int memberCount = repository.countMembers(studyServerId);
        List<UUID> previewIds = members.stream().map(StudyServerMember::userId).limit(4).toList();
        Map<UUID, AuthUserProfile> profiles = new LinkedHashMap<>();
        if (!previewIds.isEmpty()) {
            authUserDirectoryClient.findByIds(previewIds)
                    .forEach(profile -> profiles.put(profile.userId(), profile));
        }
        List<StudyServerMemberPreview> preview = previewIds.stream()
                .map(userId -> {
                    AuthUserProfile profile = profiles.get(userId);
                    String displayName = profile == null || profile.displayName() == null || profile.displayName().isBlank()
                            ? "Member"
                            : profile.displayName();
                    return new StudyServerMemberPreview(userId, displayName);
                })
                .toList();
        return new StudyServerMemberSummary(memberCount, preview);
    }

    public record StudyServerMemberDetails(
            UUID userId,
            String displayName,
            String email,
            String role,
            boolean staff
    ) {
    }

    public record StudyServerMemberDirectory(
            List<StudyServerMemberDetails> members,
            int filteredTotal,
            int memberCount
    ) {
    }

    public record StudyServerMemberPreview(UUID userId, String displayName) {
    }

    public record StudyServerMemberSummary(int memberCount, List<StudyServerMemberPreview> preview) {
    }

    private static boolean isStaffRole(String role) {
        return "OWNER".equals(role) || "INSTRUCTOR".equals(role) || "TA".equals(role);
    }

    private static boolean matchesFilter(StudyServerMemberDetails member, String filter) {
        return switch (filter) {
            case "ALL" -> true;
            case "STAFF" -> member.staff();
            case "LEARNERS" -> !member.staff();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown member filter");
        };
    }

    private static boolean matchesSearch(StudyServerMemberDetails member, String search) {
        if (search.isBlank()) {
            return true;
        }
        String haystack = (member.displayName() + " " + (member.email() == null ? "" : member.email()))
                .toLowerCase(Locale.ROOT);
        return haystack.contains(search);
    }

    public Optional<StudyServer> findStudyServer(UUID id) {
        return repository.findById(id);
    }

    public void deleteStudyServer(UUID studyServerId, UUID requesterUserId) {
        StudyServer studyServer = repository.findById(studyServerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server not found"));
        if (!studyServer.ownerRole().userId().equals(requesterUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the Study Server owner can delete this server"
            );
        }
        repository.deleteById(studyServerId);
    }

    public VoicePresence joinVoiceChannel(UUID channelId, UUID memberUserId) {
        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), memberUserId);

        return repository.saveVoicePresence(channelId, memberUserId);
    }

    public List<VoicePresence> findVoicePresences(UUID channelId, UUID viewerUserId) {
        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), viewerUserId);

        return repository.findVoicePresences(channelId);
    }

    public void leaveVoiceChannel(UUID channelId, UUID memberUserId) {
        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), memberUserId);

        repository.deleteVoicePresence(channelId, memberUserId);
    }

    public VoiceMediaToken issueVoiceChannelMediaToken(UUID channelId, UUID memberUserId) {
        StudyServerChannel channel = requireVoiceChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), memberUserId);
        VoicePresence presence = repository.saveVoicePresence(channelId, memberUserId);
        return liveKitTokenIssuer.issueForVoiceChannel(
                channelId,
                memberUserId,
                presence.canSpeak(),
                presence.canListen()
        );
    }

    public TextChannelMessageAccess findStudyServerChannelMessageAccess(UUID channelId, UUID userId) {
        StudyServerChannel channel = requireTextChannel(channelId);
        requireStudyServerMember(channel.studyServerId(), userId);

        boolean canPost = !isAnnouncementsChannel(channel.name())
                || isStudyServerOwner(channel.studyServerId(), userId);

        return new TextChannelMessageAccess(
                channel.id(),
                channel.studyServerId(),
                channel.name(),
                true,
                canPost
        );
    }

    private boolean isStudyServerOwner(UUID studyServerId, UUID userId) {
        return repository.findById(studyServerId)
                .map(server -> server.ownerRole().userId().equals(userId))
                .orElse(false);
    }

    private static boolean isAnnouncementsChannel(String channelName) {
        return "announcements".equalsIgnoreCase(channelName);
    }

    private StudyServerChannel requireTextChannel(UUID channelId) {
        StudyServerChannel channel = repository.findChannelById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server Channel not found"));
        if (channel.kind() != ChannelKind.TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study Server Channel is not a Text Channel");
        }
        return channel;
    }

    private StudyServerChannel requireVoiceChannel(UUID channelId) {
        StudyServerChannel channel = repository.findChannelById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Study Server Channel not found"));
        if (channel.kind() != ChannelKind.VOICE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Study Server Channel is not a Voice Channel");
        }
        return channel;
    }

    private void requireStudyServerMember(UUID studyServerId, UUID userId) {
        if (!repository.isStudyServerMember(studyServerId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Voice Channel access requires Study Server membership");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
