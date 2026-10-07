package dev.achiri.multivault.tenant;

import dev.achiri.multivault.tenant.model.TenantMember;
import dev.achiri.multivault.tenant.repository.TenantMemberRepository;
import dev.achiri.multivault.tenant.service.TenantMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantMemberServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @InjectMocks
    private TenantMemberService tenantMemberService;

    @Test
    void createsMemberWhenSubjectIsUnknown() {
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_new"))
                .thenReturn(Optional.empty());
        when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(call -> call.getArgument(0));

        TenantMember member = tenantMemberService.upsert(TENANT_ID, "sub_new", "nuevo@acme.com", "Nuevo");

        assertThat(member.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(member.getSubject()).isEqualTo("sub_new");
        assertThat(member.getEmail()).isEqualTo("nuevo@acme.com");
        assertThat(member.getFirstSeenAt()).isNotNull();
        verify(tenantMemberRepository).save(any(TenantMember.class));
    }

    @Test
    void updatesEmailWhenItMatchesIgnoringCaseAndSpaces() {
        TenantMember existing = memberWithEmail("admin@acme.com");
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_admin"))
                .thenReturn(Optional.of(existing));

        TenantMember member = tenantMemberService.upsert(TENANT_ID, "sub_admin", "  Admin@ACME.com ", "Admin");

        assertThat(member.getEmail()).isEqualTo("Admin@ACME.com");
    }

    @Test
    void keepsDeclaredEmailWhenIncomingEmailDiffers() {
        TenantMember existing = memberWithEmail("admin@acme.com");
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_admin"))
                .thenReturn(Optional.of(existing));

        TenantMember member = tenantMemberService.upsert(TENANT_ID, "sub_admin", "otro@evil.com", "Admin");

        assertThat(member.getEmail()).isEqualTo("admin@acme.com");
        assertThat(member.getDisplayName()).isEqualTo("Admin");
    }

    @Test
    void acceptsEmailWhenMemberHasNoneStored() {
        TenantMember existing = memberWithEmail(null);
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_admin"))
                .thenReturn(Optional.of(existing));

        TenantMember member = tenantMemberService.upsert(TENANT_ID, "sub_admin", "admin@acme.com", null);

        assertThat(member.getEmail()).isEqualTo("admin@acme.com");
    }

    @Test
    void leavesEmailUntouchedWhenIncomingEmailIsNull() {
        TenantMember existing = memberWithEmail("admin@acme.com");
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_admin"))
                .thenReturn(Optional.of(existing));

        TenantMember member = tenantMemberService.upsert(TENANT_ID, "sub_admin", null, "Admin");

        assertThat(member.getEmail()).isEqualTo("admin@acme.com");
    }

    @Test
    void treatsBlankEmailAsAbsent() {
        TenantMember existing = memberWithEmail("admin@acme.com");
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_admin"))
                .thenReturn(Optional.of(existing));

        TenantMember member = tenantMemberService.upsert(TENANT_ID, "sub_admin", "   ", "Admin");

        assertThat(member.getEmail()).isEqualTo("admin@acme.com");
    }

    @Test
    void neverSavesWhenMemberAlreadyExists() {
        TenantMember existing = memberWithEmail("admin@acme.com");
        when(tenantMemberRepository.findByTenantIdAndSubject(TENANT_ID, "sub_admin"))
                .thenReturn(Optional.of(existing));

        tenantMemberService.upsert(TENANT_ID, "sub_admin", "admin@acme.com", "Admin");

        verify(tenantMemberRepository, never()).save(any(TenantMember.class));
    }

    private TenantMember memberWithEmail(String email) {
        TenantMember member = new TenantMember();
        member.setTenantId(TENANT_ID);
        member.setSubject("sub_admin");
        member.setEmail(email);
        member.setIsActive(true);
        return member;
    }
}