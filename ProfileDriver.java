package com.eecs3311.profilemicroservice;

public interface ProfileDriver {
	DbQueryStatus createUserProfile(String userName, String fullName, String password);
	DbQueryStatus followFriend(String userName, String frndUserName);
	DbQueryStatus unfollowFriend(String userName, String frndUserName );
	DbQueryStatus getAllSongFriendsLike(String userName);
	//DbQueryStatus checkUserExists(String userName);

	//DbQueryStatus blockFriend(String userName, String friendUserName);	//first feature
	//DbQueryStatus sendSongToFriend(String senderUserName, String receiverUserName, String songId);	//second feature
}