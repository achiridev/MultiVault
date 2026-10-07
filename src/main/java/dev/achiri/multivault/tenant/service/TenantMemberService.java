package dev.achiri.multivault.tenant.service;

import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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
            if (displayName != null) {
                member.setDisplayName(displayName);
            }
            applyEmail(member, email);
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

    private void applyEmail(TenantMember member, String email) {
        String incoming = normalizeEmail(email);
        if (incoming == null) {
            return;
        }
        if (normalizeEmail(member.getEmail()) == null || incoming.equals(normalizeEmail(member.getEmail()))) {
            member.setEmail(email.trim());
            return;
        }
        log.warn("El email del miembro {} del tenant {} no coincide con el declarado; se conserva el declarado",
                member.getSubject(), member.getTenantId());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}