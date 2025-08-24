package com.eecs3311.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Values;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Profile constraints already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}
	/**
	 * This method grants the user to create a profile for themselves and store their information on the Neo4j database accordingly
	 * @param userName this will take and store the users desired username
	 * @param fullName this will take and store the users full name
	 * @param password this will take and store the users input and chosen password
	 * @return returns the status of the database after being ran and should display a message whether the
	 * users profile has been successfully created or not, display an error message if exists
	 */
	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try(Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE (p:profile {userName: $userName, fullName: $fullName, password: $password})";
				trans.run(queryStr, Values.parameters("userName", userName, "fullName", fullName, "password", password));
				trans.success();
				DbQueryStatus queryStatus = new DbQueryStatus("User Created! ", DbQueryExecResult.QUERY_OK);
				return queryStatus;
			}catch (Exception e) {
				DbQueryStatus queryStatus = new DbQueryStatus("Error: " + e.getMessage(),DbQueryExecResult.QUERY_ERROR_GENERIC);
				return queryStatus;
			}
		}
	}
	/**
	 * This method gives the users an option to follow another friend by locating their friends profile by finding their username
	 * @param userName this will take and store the users username
	 * @param frndUserName this will check for the friends username whether it exists or not and allow the user to follow them
	 * @return returns the status of the database after being ran and should display a message whether the friend
	 * has been successfully followed or not, display an error message is exists
	 */
	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {
		DbQueryStatus queryStatus = null;

		if (userName.equals(frndUserName)) {
			queryStatus = new DbQueryStatus("Cannot follow yourself", DbQueryExecResult.QUERY_ERROR_GENERIC);
			return queryStatus;
		}

		String queryStr;
		String checkUserExistsQuery;
		String checkAlreadyFollowsQuery;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {

				checkUserExistsQuery = "MATCH (p:profile) WHERE p.userName = $userName OR p.userName = $frndUserName RETURN count(p) AS count";
				StatementResult result = trans.run(checkUserExistsQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				if (result.single().get("count").asInt() < 2) {
					queryStatus = new DbQueryStatus("One or both users do not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					return queryStatus;
				}
				checkAlreadyFollowsQuery = "MATCH (p1:profile {userName: $userName})-[:follows]->(p2:profile {userName: $frndUserName}) RETURN count(p1) AS count";
				result = trans.run(checkAlreadyFollowsQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				if (result.single().get("count").asInt() > 0) {
					queryStatus = new DbQueryStatus("Already following this user", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return queryStatus;
				}
				queryStr = "MATCH (p1:profile {userName: $userName}), (p2:profile {userName: $frndUserName}) CREATE (p1)-[:follows]->(p2)";
				trans.run(queryStr, Values.parameters("userName", userName, "frndUserName", frndUserName));
				trans.success();
				queryStatus = new DbQueryStatus("Friend followed successfully", DbQueryExecResult.QUERY_OK);

			} catch (Exception e) {
				queryStatus = new DbQueryStatus("Error: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}

		return queryStatus;
	}
	/**
	 * This method gives the users an option to unfollow another user by locating their friends profile by finding their username
	 * @param userName this will take and store the users username
	 * @param frndUserName this will check for the friends username whether it exists or not and allow the user to follow them
	 * @return returns the status of the database after being ran and should display a message whether the friend
	 * has been successfully unfollowed or not, display an error message is exists
	 */
	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		if (userName.equals(frndUserName)) {
			return new DbQueryStatus("Cannot unfollow yourself", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		String checkUserExistsQuery;
		String checkRelationshipExistsQuery;
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Check if both user and friend exist
				checkUserExistsQuery = "MATCH (p:profile) WHERE p.userName = $userName OR p.userName = $frndUserName RETURN count(p) AS count";
				StatementResult userExistsResult = trans.run(checkUserExistsQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				if (userExistsResult.single().get("count").asInt() < 2) {
					return new DbQueryStatus("One or both users do not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the user is currently following the friend
				checkRelationshipExistsQuery = "MATCH (p1:profile {userName: $userName})-[f:follows]->(p2:profile {userName: $frndUserName}) RETURN count(f) AS count";
				StatementResult relationshipExistsResult = trans.run(checkRelationshipExistsQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				if (relationshipExistsResult.single().get("count").asInt() == 0) {
					return new DbQueryStatus("User is not following this friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// If the relationship exists, delete it
				queryStr = "MATCH (p1:profile {userName: $userName})-[f:follows]->(p2:profile {userName: $frndUserName}) DELETE f";
				trans.run(queryStr, Values.parameters("userName", userName, "frndUserName", frndUserName));
				trans.success();

				return new DbQueryStatus("Friend unfollowed successfully", DbQueryExecResult.QUERY_OK);

			} catch (Exception e) {
				return new DbQueryStatus("Error: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}
	}
	/**
	 * This method should get all the songs in a list based on the username profile searched from their playlist
	 * @param userName this will take the username searched for to access their playlist
	 * @return returns the status of the database after being ran and should return and display the list with all songs that
	 * friends like, display an error message if exists
	 */
	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "MATCH (p:profile {userName: $userName})-[:follows]->(friend:profile)-[:likes]->(song:Song) RETURN friend.userName, COLLECT(song.title) AS likedSongs";
				StatementResult result = trans.run(queryStr, Values.parameters("userName", userName));
				trans.success();
				Map<String, List<String>> songs = new HashMap<>();

				while (result.hasNext()) {
					Record record = result.next();
					String friendUserName = record.get("friend.userName").asString();
					List<String> likedSongs = record.get("likedSongs").asList(value -> value.asString());
					songs.put(friendUserName, likedSongs);
				}

				DbQueryStatus queryStatus = new DbQueryStatus("Friend's song list created!", DbQueryExecResult.QUERY_OK);
				queryStatus.setData(songs);
				return queryStatus;
			} catch (Exception e) {
				DbQueryStatus queryStatus = new DbQueryStatus("Error: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				return queryStatus;
			}
		}
	}
	/**
	 * This method should allow the user to be able to send a specific song to their friend by navigating the song by sending
	 * their friend the song ID
	 * @param senderUserName this is the username of the user sending a song to their friend
	 * @param receiverUserName this is the username of the user receiving a song from their friend
	 * @param songId this is the songId that will be sent between users to navigate and locate the specific song being
	 * sent and received
	 * @return returns the status of the database after being ran and displays a message whether the song has been
	 * successfully sent and to whom or not, display an error message if exists
	 */
	public DbQueryStatus sendSongToFriend(String senderUserName, String receiverUserName, String songId) {
		String queryStr;
		Map<String, Object> songData = new HashMap<>();
		String checkUserExistsQuery;
		DbQueryStatus queryStatus = null;
		String checkNotFollowsQuery;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				// Retrieve the song name for the response (optional)
				String getSongNameQuery = "MATCH (song:song {songId: $songId}) RETURN song.songName as songName";
				StatementResult songResult = trans.run(getSongNameQuery, Values.parameters("songId", songId));

				checkUserExistsQuery = "MATCH (p:profile) WHERE p.userName = $userName OR p.userName = $frndUserName RETURN count(p) AS count";
				StatementResult result = trans.run(checkUserExistsQuery, Values.parameters("userName", senderUserName, "frndUserName", receiverUserName));
				if (result.single().get("count").asInt() < 2) {
					queryStatus = new DbQueryStatus("One or both users do not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					return queryStatus;
				}

				checkNotFollowsQuery = "MATCH (sender:profile {userName: $userName})-[:follows]->(receiver:profile {userName: $frndUserName}) RETURN count(receiver) AS count";
				result = trans.run(checkNotFollowsQuery, Values.parameters("userName", senderUserName, "frndUserName", receiverUserName));
				if (result.single().get("count").asInt() == 0) {
					queryStatus = new DbQueryStatus("Must be following the user to send a song", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return queryStatus;
				}

				if (songResult.hasNext()) {
					String songName = songResult.single().get("songName").asString();
					songData.put("songId", songId);
					songData.put("songName", songName);
				} else {
					return new DbQueryStatus("Song not found.", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Create relationships for sending a song
				queryStr = "MATCH (sender:profile {userName: $senderUserName}), (receiver:profile {userName: $receiverUserName}), (song:song {songId: $songId}) " +
						"MERGE (sender)-[:sent_song]->(song)-[:received_by]->(receiver)";
				trans.run(queryStr, Values.parameters("senderUserName", senderUserName, "receiverUserName", receiverUserName, "songId", songId));
				trans.success();
				queryStatus = new DbQueryStatus("Song sent successfully to " + receiverUserName + ".", DbQueryExecResult.QUERY_OK);
				queryStatus.setData(songData);
				return queryStatus;
			} catch (Exception e) {
				return new DbQueryStatus("Failed to send the song. Please check the provided details.", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}
	}
	/**
	 * This method allows for the user to block another friend or profile by locating their username
	 * @param userName this will take the username wanting to block someone
	 * @param frndUserName this will take the username of the user that is going to be blocked
	 * @return returns the status of the database after being ran and displays a message whether the user has been blocked or not,
	 * display an error message if exists
	 */
	public DbQueryStatus blockFriend(String userName, String frndUserName) {
		String queryStr;
		String deleteFollowRelQuery;
		String createBlockRelQuery;
		String checkUserExistsQuery;
		DbQueryStatus queryStatus = null;
		String checkAlreadyBlockedQuery;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				checkUserExistsQuery = "MATCH (p:profile) WHERE p.userName = $userName OR p.userName = $frndUserName RETURN count(p) AS count";
				StatementResult result = trans.run(checkUserExistsQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				if (result.single().get("count").asInt() < 2) {
					queryStatus = new DbQueryStatus("One or both users do not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					return queryStatus;
				}

				checkAlreadyBlockedQuery = "MATCH (p1:profile {userName: $userName})-[:blocked]->(p2:profile {userName: $frndUserName}) RETURN count(p1) AS count";
				result = trans.run(checkAlreadyBlockedQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				if (result.single().get("count").asInt() > 0) {
					queryStatus = new DbQueryStatus("Already Blocked this user", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return queryStatus;
				}

				// First, delete any existing 'follows' relationship
				deleteFollowRelQuery = "MATCH (p1:profile {userName: $userName})-[f:follows]->(p2:profile {userName: $frndUserName}) DELETE f";
				trans.run(deleteFollowRelQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));

				// Then, create a 'blocked' relationship
				createBlockRelQuery = "MATCH (p1:profile {userName: $userName}), (p2:profile {userName: $frndUserName}) MERGE (p1)-[:blocked]->(p2)";
				trans.run(createBlockRelQuery, Values.parameters("userName", userName, "frndUserName", frndUserName));
				trans.success();
				queryStatus = new DbQueryStatus("Friend Blocked!", DbQueryExecResult.QUERY_OK);
				return queryStatus;

			} catch (Exception e) {
				queryStatus = new DbQueryStatus("Error: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				return queryStatus;
			}
		}
	}
}
