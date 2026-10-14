package br.org.pascom.model;

import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "tb_usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome é obrigatório")
    @Column(nullable = false)
    private String nome;

    @Email
    @NotBlank(message = "O e-mail é obrigatório")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres")
    @Column(nullable = false)
    @JsonIgnore
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Nulo para COORDENADOR_GERAL (coordena tudo); obrigatório para COORDENADOR e VOLUNTARIO. */
    @Enumerated(EnumType.STRING)
    private Setor setor;

    private String funcao;
    private String disponibilidade;
    private Integer avatarHue;

    @Column(unique = true)
    @JsonIgnore
    private String resetSenhaToken;

    @JsonIgnore
    private LocalDateTime resetSenhaExpiraEm;

    /**
     * Flag de administrador (ex.: a desenvolvedora da aplicação): dá os mesmos poderes de
     * Coordenador Geral em todo o sistema, independente do papel/setor cadastrado. Fica só no
     * banco — nunca é definida pela API pública; é ligada na inicialização a partir da env var
     * ADMIN_EMAILS (ver InicializacaoDados) ou direto no banco.
     */
    @Column(name = "admin_sistema", columnDefinition = "boolean default false")
    @JsonIgnore
    private Boolean admin;

    /** Padrão único de e-mail no sistema: sem espaços nas pontas e sempre em minúsculas. */
    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @JsonIgnore
    public boolean isAdmin() {
        return Boolean.TRUE.equals(admin);
    }

    /** true para quem é COORDENADOR_GERAL de verdade, ou para quem tem a flag admin. */
    @JsonIgnore
    public boolean temPoderesDeCoordenadorGeral() {
        return role == Role.COORDENADOR_GERAL || isAdmin();
    }

    /** true para Coordenador(a) de setor, Coordenador(a) Geral ou admin. */
    @JsonIgnore
    public boolean isCoordenacao() {
        return role == Role.COORDENADOR || temPoderesDeCoordenadorGeral();
    }

    /** true se a pessoa coordena o setor informado (ou tem poderes de Coordenador Geral, que coordena todos). */
    @JsonIgnore
    public boolean coordenaSetor(Setor alvo) {
        return temPoderesDeCoordenadorGeral() || (role == Role.COORDENADOR && setor != null && setor == alvo);
    }

    /** Quem pode marcar a conferência doutrinária de um cartão. */
    @JsonIgnore
    public boolean podeConferirDoutrina() {
        return isCoordenacao();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}