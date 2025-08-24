package com.eecs3311.profilemicroservice;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Values;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Playlist constraint already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}
	/**
	 * This method will add a liked song to the users playlist and should check for a correct path according to the database
	 * that should store these liked songs
	 * @param userName this creates a string for the users user name to navigate to their profile in the database and make
	 * sure they match
	 * @param songId this assigns a song Id for every song to differentiate and navigate between them
	 * @return returns the status of the database after being ran and should display a message whether the song
	 * had been liked or not, and display an error message if exists
	 */
	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		String queryStr;
		String checkUserExistsQuery, checkSongExistsQuery, checkRelationshipExistsQuery, addToFavoritesQuery;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {

				// Check if the song exists
				checkSongExistsQuery = "MATCH (s:song {songId: $songId}) RETURN count(s) AS count";
				StatementResult songExistsResult = trans.run(checkSongExistsQuery, Values.parameters("songId", songId));
				if (songExistsResult.single().get("count").asInt() == 0) {
					return new DbQueryStatus("Song does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the user exists
				checkUserExistsQuery = "MATCH (p:profile {userName: $userName}) RETURN count(p) AS count";
				StatementResult userExistsResult = trans.run(checkUserExistsQuery, Values.parameters("userName", userName));
				if (userExistsResult.single().get("count").asInt() == 0) {
					return new DbQueryStatus("User does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the user has already liked the song
				checkRelationshipExistsQuery = "MATCH (p:profile {userName: $userName})-[r:likes]->(s:song {songId: $songId}) RETURN count(r) AS count";
				StatementResult relationshipExistsResult = trans.run(checkRelationshipExistsQuery, Values.parameters("userName", userName, "songId", songId));
				if (relationshipExistsResult.single().get("count").asInt() != 0) {
					return new DbQueryStatus("Song is already liked by user", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Like the song by creating a likes relationship
				queryStr = "MATCH (p:profile {userName: $userName}), (s:song {songId: $songId}) MERGE (p)-[:likes]->(s)";
				trans.run(queryStr, Values.parameters("userName", userName, "songId", songId));

				// Add the song to user's favorites playlist
				addToFavoritesQuery = "MATCH (pl:playlist {plName: $userName + '-favorites'}), (s:song {songId: $songId}) CREATE (pl)-[:includes]->(s)";
				trans.run(addToFavoritesQuery, Values.parameters("userName", userName, "songId", songId));

				trans.success();

				DbQueryStatus queryStatus = new DbQueryStatus("Song liked and added to playlist!", DbQueryExecResult.QUERY_OK);
				return queryStatus;
			} catch (Exception e) {
				DbQueryStatus queryStatus = new DbQueryStatus("Error: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				return queryStatus;
			}
		}
	}

	/**
	 * This method will remove a previously liked song from the users playlist while following the correct nodes given
	 * in the database
	 * @param userName this creates a string for the users user name to navigate to their profile in the database and make
	 * sure they match
	 * @param songId this assigns a song Id for every song to differentiate and navigate between them
	 * @return returns the status of the database after being ran and should display a message whether the song has been
	 * unliked successfully or not, and display an error message if exists
	 */
	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		String queryStr;
		String checkUserExistsQuery, checkSongExistsQuery, checkRelationshipExistsQuery, deleteFromFavoritesQuery;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try(Transaction trans = session.beginTransaction()) {

				// Check if the song exists
				checkSongExistsQuery = "MATCH (s:song {songId: $songId}) RETURN count(s) AS count";
				StatementResult songExistsResult = trans.run(checkSongExistsQuery, Values.parameters("songId", songId));
				if (songExistsResult.single().get("count").asInt() == 0) {
					return new DbQueryStatus("Song does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the user exists
				checkUserExistsQuery = "MATCH (p:profile {userName: $userName}) RETURN count(p) AS count";
				StatementResult userExistsResult = trans.run(checkUserExistsQuery, Values.parameters("userName", userName));
				if (userExistsResult.single().get("count").asInt() == 0) {
					return new DbQueryStatus("User does not exist", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				}

				// Check if the user has liked the song
				checkRelationshipExistsQuery = "MATCH (p:profile {userName: $userName})-[r:likes]->(s:song {songId: $songId}) RETURN count(r) AS count";
				StatementResult relationshipExistsResult = trans.run(checkRelationshipExistsQuery, Values.parameters("userName", userName, "songId", songId));
				if (relationshipExistsResult.single().get("count").asInt() == 0) {
					return new DbQueryStatus("Song is not liked by user", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}

				// Remove the song from user's favorites playlist
				String playlistName = userName + "-favorites";
				deleteFromFavoritesQuery = "MATCH (pl:playlist {plName: $plName})-[r:includes]->(s:song {songId: $songId}) DELETE r";
				trans.run(deleteFromFavoritesQuery, Values.parameters("plName", playlistName, "songId", songId));
				trans.success();


				// Unlike the song
				queryStr = "MATCH (p:profile {userName: $userName})-[r:likes]->(s:song {songId: $songId}) DELETE r";
				trans.run(queryStr, Values.parameters("userName", userName, "songId", songId));
				trans.success();

				DbQueryStatus queryStatus = new DbQueryStatus("Song Unliked and removed from playlist! ", DbQueryExecResult.QUERY_OK);
				return queryStatus;
			}catch (Exception e) {
				DbQueryStatus queryStatus = new DbQueryStatus("Error: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
				return queryStatus;
			}
		}
	}

}
