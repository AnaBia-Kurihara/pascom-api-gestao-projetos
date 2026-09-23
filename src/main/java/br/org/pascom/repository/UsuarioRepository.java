package br.org.pascom.repository;

import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByRole(Role role);
    Optional<Usuario> findByResetSenhaToken(String resetSenhaToken);
}