package com.socialfeed.repository;

import com.socialfeed.model.Follow;
import com.socialfeed.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerAndFollowee(User follower, User followee);
    boolean existsByFollowerAndFollowee(User follower, User followee);
    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
    long countByFollowerId(Long followerId);
    long countByFolloweeId(Long followeeId);
    
    @Query("SELECT f.followee.id FROM Follow f WHERE f.follower.id = :userId")
    List<Long> findFolloweeIdsByFollowerId(@Param("userId") Long userId);
    
    @Query("SELECT f.follower.id FROM Follow f WHERE f.followee.id = :userId")
    List<Long> findFollowerIdsByFolloweeId(@Param("userId") Long userId);
}
