package com.eecs3311.songmicroservice;
// DO NOT TOUCH CAUSE IT WORKS
// IF IT WORKS IT WORK OK
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;




@Repository
public class SongDalImpl implements SongDal {

	private final MongoTemplate db;

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	/**
	 * This method adds the specified song to the mongoDB
	 * @param songToAdd this stores the data of the song chosen to add to the database
	 * @return returns the status of the database after being ran and displays a message whether the song has been successfully
	 * added to the mongoDB or not, display an error message if exists
	 */
	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		try {
			db.insert(songToAdd);
			DbQueryStatus dbQueryStatus = new DbQueryStatus("Added the song", DbQueryExecResult.QUERY_OK);
			dbQueryStatus.setData(songToAdd);
			return dbQueryStatus;
		} catch (Exception e) {
			// Handle the exception.
			return new DbQueryStatus("Error occurred while adding song", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
	/**
	 * This method should be able to find a specific song given the Id of the song to search for
	 * @param songId this reads the input of the song Id to search for
	 * @return returns the status of the database after being ran and displays a message whether the song has been successfully
	 * located and stored in the mongoDB or not, display an error message if exists
	 */
	@Override
	public DbQueryStatus findSongById(String songId) {
		try {
			Song song = db.findById(songId, Song.class);

			if (song == null) {
				return new DbQueryStatus("Song not found in DB :(", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}

			DbQueryStatus dbQueryStatus = new DbQueryStatus("Song found in DB!", DbQueryExecResult.QUERY_OK);
			dbQueryStatus.setData(song);
			return dbQueryStatus;
		} catch (Exception e) {
			return new DbQueryStatus("Error occurred during database query", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
	/**
	 * This method should be able to return the name of the song based on the given Id of the song to get
	 * @param songId this reads the input of the song Id to search for
	 * @return returns the status of the database after being ran and displays a message whether the correct song title has been
	 * returned or not, display an error message if exists
	 */
	@Override
	public DbQueryStatus getSongTitleById(String songId) {
		try {
			Song song = db.findById(songId, Song.class);


			if (song == null) {
				return new DbQueryStatus("No song found in DB", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}
			DbQueryStatus dbQueryStatus = new DbQueryStatus("Song found in DB", DbQueryExecResult.QUERY_OK);
			dbQueryStatus.setData(song.getSongName());

			return dbQueryStatus;
		} catch (Exception e) {
			return new DbQueryStatus("Error occurred while accessing the database", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	/**
	 * This method should be able to delete a song from the mongoDB given its relative Id
	 * @param songId this reads the input of the song Id to search for and delete
	 * @return returns the status of the database after being ran and displays a message whether the song has been successfully
	 * deleted completely from the mongoDB or not, display an error message if exists
	 */
	@Override
	public DbQueryStatus deleteSongById(String songId) {
		try {
			Song song = db.findById(songId, Song.class);

			// Check if the song exists in the database
			if (song == null) {
				String message = String.format("No song found in DB with ID: %s", songId);
				return new DbQueryStatus(message, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}

			// Remove the song from the database
			this.db.remove(song);
			String successMessage = String.format("Removed song with ID: %s from DB", songId);
			return new DbQueryStatus(successMessage, DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			return new DbQueryStatus("Error occurred while accessing the database", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	/**
	 * This method should be able to update the count of the users that liked and added a certain song to their playlist by
	 * being able to locate the song through its Id, and this method is expected to also decerement the count if a user decides
	 * to unlike the song and remove it from their playlist
	 * @param songId this reads the input of the song Id
	 * @param shouldDecrement this will decrement the count if a user decides to unlike and remove the song from their playlist
	 * @return returns the status of the database after being ran and displays a message whether the count of the users that are
	 * liking/unliking a certain song is correctly displayed and updated properly, and is the song Id is correct and able to be
	 * located, display an error message if exists
	 */
	@Override
	public DbQueryStatus updateSongFavouritesCount(String songId, boolean shouldDecrement) {
		try {
			Song exist = db.findById(songId, Song.class);
			if (exist == null) {
				return new DbQueryStatus("No song found in DB with ID: " + songId, DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			}

			long favouritesCount = exist.getSongAmountFavourites(); // Changed to 'long'
			if (favouritesCount == 0 && shouldDecrement) {
				return new DbQueryStatus("Cannot decrement favourites: Song with ID " + songId + " already has 0 favourites", DbQueryExecResult.QUERY_ERROR_GENERIC);
			}

			long adjustment = shouldDecrement ? -1 : 1;
			exist.setSongAmountFavourites(favouritesCount + adjustment);

			Query query = new Query(Criteria.where("_id").is(songId));
			this.db.findAndReplace(query, exist);

			String message = shouldDecrement ? "Decreased favourite count by 1 for song ID " + songId : "Increased favourite count by 1 for song ID " + songId;
			return new DbQueryStatus(message, DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			return new DbQueryStatus("Error occurred while updating the song's favourites in the database", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
}
