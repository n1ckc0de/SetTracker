package com.gymsync.api.program.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymsync.api.program.domain.Program;
import com.gymsync.api.user.domain.Role;
import com.gymsync.api.user.domain.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class ProgramSpecificationsTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void forUser_buildsEqualPredicateOnUser() {
    User user = new User("spec@example.com", "hash", "Spec", Role.USER);
    Specification<Program> spec = ProgramSpecifications.forUser(user);

    Root<Program> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    Path userPath = mock(Path.class);
    Predicate expected = mock(Predicate.class);
    when(root.get("user")).thenReturn(userPath);
    when(cb.equal(userPath, user)).thenReturn(expected);

    Predicate result = spec.toPredicate(root, query, cb);
    assertThat(result).isSameAs(expected);
    verify(cb).equal(userPath, user);
  }
}
