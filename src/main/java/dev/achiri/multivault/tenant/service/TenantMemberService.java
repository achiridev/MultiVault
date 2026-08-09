package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantMemberService {

    private final TenantMemberRepository tenantMemberRepository;

    @Transactional
    public TenantMember upsert(UUID tenantId, String subject, String email, String displayName) {
        Optional<TenantMember> existing = tenantMemberRepository.findByTenantIdAndSubject(tenantId, subject);
        if (existing.isPresent()) {
            TenantMember member = existing.get();
            member.setLastSeenAt(Instant.now());
            if (email != null) {
                member.setEmail(email);
            }
            if (displayName != null) {
                member.setDisplayName(displayName);
            }
            return member;
        }
        TenantMember member = new TenantMember();
        member.setTenantId(tenantId);
        member.setSubject(subject);
        member.setEmail(email);
        member.setDisplayName(displayName);
        member.setFirstSeenAt(Instant.now());
        member.setLastSeenAt(Instant.now());
        return tenantMemberRepository.save(member);
    }
}
