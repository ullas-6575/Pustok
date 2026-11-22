package com.example.EPustok.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    /** Id and display name only; the password hash is never selected. */
    interface UserName {
        Long getId();
        String getName();
    }

    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select u.id as id, u.name as name from User u where u.id in :ids")
    List<UserName> findNamesByIdIn(@Param("ids") Collection<Long> ids);
}
