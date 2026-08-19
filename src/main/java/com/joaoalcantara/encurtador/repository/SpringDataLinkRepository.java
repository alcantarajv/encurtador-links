package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.Link;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio do Spring Data JPA.
 *
 * Nao tem implementacao: o Spring gera a classe em tempo de execucao a partir
 * da assinatura dos metodos. "existsByCode" vira "SELECT ... WHERE code = ?"
 * porque o nome segue a convencao <operacao>By<Campo>.
 *
 * Esta interface e detalhe de infraestrutura -- so o adaptador JpaLinkRepository
 * a enxerga. O servico continua conversando com a porta LinkRepository.
 */
public interface SpringDataLinkRepository extends JpaRepository<Link, Long> {

    boolean existsByCode(String code);

    Optional<Link> findByCode(String code);
}
