package com.socialfeed.service;

import com.socialfeed.dto.UserDto;
import com.socialfeed.dto.UserProfileDto;
import com.socialfeed.dto.UserReputationHistoryDto;
import com.socialfeed.dto.UserSearchDto;
import com.socialfeed.dto.TopicReputationDto;
import com.socialfeed.model.Follow;
import com.socialfeed.model.User;
import com.socialfeed.repository.FollowRepository;
import com.socialfeed.repository.PostRepository;
import com.socialfeed.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int FOLLOW_BACKFILL_MAX_POSTS = 20;

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final TimelineService timelineService;
    private final TopicReputationService topicReputationService;
    private final ReputationHistoryService reputationHistoryService;

    public UserDto getUserById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return toDto(user);
    }

    public UserDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return toDto(user);
    }

    public List<UserSearchDto> searchUsersByUsername(String query, Long currentUserId) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return List.of();
        }

        return userRepository.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(trimmedQuery)
            .stream()
            .map(user -> {
                boolean me = user.getId().equals(currentUserId);
                boolean following = !me && followRepository.existsByFollowerIdAndFolloweeId(currentUserId, user.getId());
                return new UserSearchDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserTrustScore(), me, following);
            })
            .collect(Collectors.toList());
    }

    public UserProfileDto getUserProfileById(Long targetUserId, Long currentUserId) {
        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        boolean me = user.getId().equals(currentUserId);
        boolean following = !me && followRepository.existsByFollowerIdAndFolloweeId(currentUserId, user.getId());

        return new UserProfileDto(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getUserTrustScore(),
            me,
            following,
            followRepository.countByFolloweeId(user.getId()),
            followRepository.countByFollowerId(user.getId()),
            postRepository.countByUserIdAndParentPostIdIsNull(user.getId()),
            topicReputationService.getTopicReputationForUser(user.getId())
        );
    }

    public UserProfileDto getUserProfileByUsername(String username, Long currentUserId) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return getUserProfileById(user.getId(), currentUserId);
    }

    @Transactional
    public void followUser(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new RuntimeException("Cannot follow yourself");
        }

        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> new RuntimeException("Follower not found"));
        User followee = userRepository.findById(followeeId)
            .orElseThrow(() -> new RuntimeException("User to follow not found"));

        if (followRepository.existsByFollowerAndFollowee(follower, followee)) {
            throw new RuntimeException("Already following this user");
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowee(followee);
        followRepository.save(follow);

        timelineService.backfillRecentPostsForFollow(followerId, followeeId, FOLLOW_BACKFILL_MAX_POSTS);
    }

    @Transactional
    public void unfollowUser(Long followerId, Long followeeId) {
        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> new RuntimeException("Follower not found"));
        User followee = userRepository.findById(followeeId)
            .orElseThrow(() -> new RuntimeException("User to unfollow not found"));

        Follow follow = followRepository.findByFollowerAndFollowee(follower, followee)
            .orElseThrow(() -> new RuntimeException("Not following this user"));

        followRepository.delete(follow);
    }

    public List<UserDto> getFollowers(Long userId) {
        List<Long> followerIds = followRepository.findFollowerIdsByFolloweeId(userId);
        return userRepository.findAllById(followerIds).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public List<UserDto> getFollowing(Long userId) {
        List<Long> followeeIds = followRepository.findFolloweeIdsByFollowerId(userId);
        return userRepository.findAllById(followeeIds).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public List<TopicReputationDto> getTopicReputation(Long userId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return topicReputationService.getTopicReputationForUser(userId);
    }

    public UserReputationHistoryDto getReputationHistory(Long userId, int limit) {
        userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return reputationHistoryService.getUserHistory(userId, limit);
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getUserTrustScore());
    }
}
