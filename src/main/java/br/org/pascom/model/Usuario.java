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

    // E-mail da desenvolvedora da aplicação — por pedido dela, tem sempre os mesmos poderes
    // de Coordenador Geral em todo o sistema, mesmo cadastrada como Coordenadora de um setor.
    private static final String EMAIL_DESENVOLVEDORA = "bibia.oliveira.kurihara@gmail.com";

    /** true para quem é COORDENADOR_GERAL de verdade, ou para a desenvolvedora da aplicação. */
    @JsonIgnore
    public boolean temPoderesDeCoordenadorGeral() {
        return role == Role.COORDENADOR_GERAL
                || (email != null && email.equalsIgnoreCase(EMAIL_DESENVOLVEDORA));
    }

    /** true para Coordenador(a) de setor, Coordenador(a) Geral, ou a desenvolvedora — quem pode marcar a conferência doutrinária de um cartão. */
    @JsonIgnore
    public boolean podeConferirDoutrina() {
        return role == Role.COORDENADOR || temPoderesDeCoordenadorGeral();
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