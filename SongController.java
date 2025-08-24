package com.eecs3311.songmicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.json.JSONObject;
import okhttp3.FormBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
// DONE FILE DO NOT TOUCH
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping
public class SongController {

	@Autowired
	private final SongDal songDal;

	private OkHttpClient client = new OkHttpClient();


	public SongController(SongDal songDal) {
		this.songDal = songDal;
	}

	/**
	 * This method is partially implemented for you to follow as an example of
	 * how to complete the implementations of methods in the controller classes.
	 * @param songId
	 * @param request
	 * @return
	 */

	/**
	 * This method should be able to store a song based on the Id the user inputs
	 * @param songId this reads the input of the user for the Id of the song
	 * @param request sends a request URL to the database when a user searches for a song from the mongoDB based on its Id
	 * @return returns the request data and set the status whether the song has been located correctly according to the given
	 * song Id from the user or not, display an error message if exists
	 */
	@RequestMapping(value = "/getSongById/{songId}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getSongById(@PathVariable("songId") String songId,
														   HttpServletRequest request) { 	// DONE DO NOT TOUCH // TESTED WITH CURL

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.findSongById(songId);

		// TODO: uncomment these two lines when you have completed the implementation of findSongById in SongDal
		response.put("message", dbQueryStatus.getMessage());


		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData()); // TODO: remove when the above 2 lines are uncommented
	}
	/**
	 * This method should be able to display the song name based on the Id given by the user and store it to the mongoDB accordingly
	 * @param songId this reads the input of the song Id from the user to get the title
	 * @param request sends a request URL to the database when a user searches for a songs title name given its respective Id
	 * @return returns the request data and set the status whether the song title has been correctly identified based on the given
	 * song Id or not, display an error message if exists
	 */
	@RequestMapping(value = "/getSongTitleById/{songId}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getSongTitleById(@PathVariable("songId") String songId,
																HttpServletRequest request) { // DONE DO NOT TOUCH // TESTED WITH CURL

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));
		// TODO: add any other values to the map following the example in getSongById
		DbQueryStatus dbQueryStatus = songDal.getSongTitleById(songId);

		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData()); // TODO: replace with return statement similar to in getSongById
	}

	/**
	 * This method should be able to identify a song given its respective Id from the user and delete it completely from the
	 * mongoDB
	 * @param songId this reads the input of the song Id from the user to delete the song
	 * @param request sends a request URL to the database when a user searches for a song to delete from the database
	 * @return returns the request data and set the status whether the song has been correctly identified and deleted from the
	 * mongoDB with its given Id respectively, display an error message if exists
	 */
	@RequestMapping(value = "/deleteSongById/{songId}", method = RequestMethod.DELETE)
	public ResponseEntity<Map<String, Object>> deleteSongById(@PathVariable("songId") String songId, HttpServletRequest request) {
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("DELETE %s", Utils.getUrl(request)));

		if (songId == null || songId.isEmpty()) { // Check if songId is null or empty
			response.put("message", "The data provided has not been fully completed");
			return Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
		} else {
			DbQueryStatus deleteSong = songDal.deleteSongById(songId);
			response.put("message", deleteSong.getMessage());
			return Utils.setResponseStatus(response, deleteSong.getdbQueryExecResult(), null);
		}
	}
	/**
	 * This method should allow the user to be able to add a song to their playlist, which then should be correctly added to the
	 * mongoDB to its respective path and collection
	 * @param params this fetches the parameter "songId" for this method
	 * @param request sends a request URL to the database when the user attempts to add a song to the mongoDB
	 * @return returns the request data and set the status whether the song has been successfully added to the mongoDB or not,
	 * display an error message if exists
	 */
	@RequestMapping(value = "/addSong", method = RequestMethod.POST)
	public ResponseEntity<Map<String, Object>> addSong(@RequestBody Map<String, String> params, HttpServletRequest request) {
		Map<String, Object> response = new HashMap<>();

		String songName = params.get("songName");
		String songArtistFullName = params.get("songArtistFullName");
		String songAlbum = params.get("songAlbum");

		// Enhanced parameter validation
		if (songName == null || songName.isEmpty() || songArtistFullName == null || songArtistFullName.isEmpty() || songAlbum == null || songAlbum.isEmpty()) {
			response.put("message", "Missing or empty required parameters");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		// Refactor object creation and database call
		DbQueryStatus dbQueryStatus = songDal.addSong(new Song(songName, songArtistFullName, songAlbum));

		response.put("path", String.format("POST %s", Utils.getUrl(request)));
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

	/**
	 * This method should be able to identify a song correclty given the Id input by the user and should display the number of
	 * people that have liked this specific song and added it to their playlist, this should also be expected to decrement that
	 * counter value if a user decides to unlike the specificed song. This method is expected to correctly add the data taken to
	 * the mongoDB.
	 * @param songId this reads the user input of the specific songId to search for the number of users that have
	 * liked/unliked the song
	 * @param shouldDecrement a boolean statement that will only decrement the count if a user has disliked the specific song
	 * @param request sends a request URL to the database when the user inputs a songId to search
	 * @return returns the request data and set the status whether the specified song that has been inputted by the user has the
	 * counter of likes/unlikes correctly displayed, updated, and stored to the mongoDB
	 */
	@RequestMapping(value = "/updateSongFavouritesCount", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> updateFavouritesCount(@RequestBody Map<String, String> params, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<>();

		String shouldDecrement = params.get("shouldDecrement");
		String songId = params.get("songId");


		if (songId == null || songId.isEmpty() || shouldDecrement == null || (!shouldDecrement.equals("true") && !shouldDecrement.equals("false"))) {
			response.put("message", "Invalid or missing parameters");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}


		DbQueryStatus dbQueryStatus = songDal.updateSongFavouritesCount(songId, Boolean.parseBoolean(shouldDecrement));


		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

}
