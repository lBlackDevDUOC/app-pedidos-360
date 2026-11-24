package cl.duoc.pedidos360.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RolesJwtConverterTest {

    private Jwt token(List<String> roles) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").claim("scp", "OT.Create")
                .claim("preferred_username", "ana@duoc.cl").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (roles != null) b.claim("roles", roles);
        return b.build();
    }

    @Test
    void traduceScopesYRolesDeEntraId() {
        var auth = new RolesJwtConverter().convert(token(List.of("Operador", "Auditor")));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_OT.Create", "ROLE_OPERADOR", "ROLE_AUDITOR");
        assertThat(auth.getName()).isEqualTo("ana@duoc.cl");
    }

    @Test
    void sinRolesEsCliente() {
        var auth = new RolesJwtConverter().convert(token(null));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_CLIENTE");
    }
}
