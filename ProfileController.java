package com.eecs3311.profilemicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.json.JSONObject;
import org.neo4j.driver.v1.Values;
import com.eecs3311.profilemicroservice.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping
public class ProfileController {
	public static final String KEY_USER_NAME = "userName";
	public static final String KEY_USER_FULLNAME = "fullName";
	public static final String KEY_USER_PASSWORD = "password";
	public static final String KEY_FRIEND_USER_NAME = "friendUserName";
	public static final String KEY_SONG_ID = "songId";

	@Autowired
	private final ProfileDriverImpl profileDriver;

	@Autowired
	private final PlaylistDriverImpl playlistDriver;

	OkHttpClient client = new OkHttpClient();


	public ProfileController(ProfileDriverImpl profileDriver, PlaylistDriverImpl playlistDriver) {
		this.profileDriver = profileDriver;
		this.playlistDriver = playlistDriver;
	}

	/**
	 * This method will add the profile of the user after the profile has been created, this method should be able to fetch the
	 * username, fullname, and password, and store it in the database, this method should allow the user to retry their entries
	 * to attempt to login by identifying and displaying what is missing to input.
	 *
	 * @param params  this gets and asks the users for the following parameters: userName, fullName, password
	 * @param request sends a request URL to the database when a user attempts to login to their profile
	 * @return returns the request data and set the status whether a user profile has been successfully added to the database or not,
	 * display an error message if exists
	 */
	@RequestMapping(value = "/profile", method = RequestMethod.POST)
	public ResponseEntity<Map<String, Object>> addProfile(@RequestBody Map<String, String> params, HttpServletRequest request) { // DONE TESTED AND IT WORKS

		Map<String, Object> response = new HashMap<String, Object>();
		String userName = null;
		String fullName = null;
		String password = null;

		userName = params.get("userName");
		fullName = params.get("fullName");
		password = params.get("password");

		// Check for BAD_REQUEST
		if (userName == null || fullName == null || password == null) {
			response.put("message", "Missing required parameters");
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}

		// Method that calls the db
		DbQueryStatus dbQueryStatus = profileDriver.createUserProfile(userName, fullName, password);

		// Response
		response.put("path", String.format("POST %s", Utils.getUrl(request)));
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

	/**
	 * This method should complete the follow friend procedure and store it in the database and store the data to display that
	 * this user now follows another user.
	 *
	 * @param params  this should get and store the following parameters for this method: userName, frndUserName
	 * @param request sends a request URL to the database when a user attempts to follow a friend
	 * @return returns the request data and set the status whether a user profile has been successfully followed or not,
	 * display an error message if exists
	 */
	@RequestMapping(value = "/followFriend", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> followFriend(@RequestBody Map<String, String> params, HttpServletRequest request) { // TESTED AND IT WORKS

		Map<String, Object> response = new HashMap<String, Object>();

		String friendUserName = params.get("friendUserName");
		String userName = params.get("userName");

		// Check for BAD_REQUEST
		if (userName == null || friendUserName == null) {
			response.put("status", HttpStatus.BAD_REQUEST);
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}

		if (userName.equals(friendUserName)) {
			response.put("status", HttpStatus.BAD_REQUEST);
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}

		// method that calls db
		DbQueryStatus dbQueryStatus = profileDriver.followFriend(userName, friendUserName);

		// Response
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		// TODO: replace with return statement similar to in getSongById
	}

	/**
	 * This method should get a list of all the song titles that a friend has liked and added to their playlist, this should also
	 * be able to convert the song Id that is being read and convert it to display it into a list of words to better identify
	 * the song
	 *
	 * @param userName this gets the username of the profile that the user wants to display their favorite song titles
	 * @param request  sends a request URL to the database when a user attempts to get a list of all the song titles the
	 *                 friends has liked
	 * @return returns the request data and set the status whether a users song titles has been displayed successfully or not,
	 * display an error message if exists
	 */
	@RequestMapping(value = "/getAllFriendFavouriteSongTitles/{userName}", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getAllFriendFavouriteSongTitles(@PathVariable("userName") String userName,
																			   HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		if(userName != null) {
			DbQueryStatus status = profileDriver.getAllSongFriendsLike(userName);
			return Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData()); // Use status directly
		} else {
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return ResponseEntity.ok(response); // Return response for the error case
		}
	}

	/**
	 * This method should store the data to the database accordingly after a user has decided to unfollow a friend, this method
	 * should also be able to completely remove the other users data from the database completely and display any error messages
	 * with respect to the users username input and identify whether the targeted user is a valid user and if there was a typo
	 * in the username or not.
	 *
	 * @param params  this should get and store the following parameters for this method: userName, frndUserName
	 * @param request sends a request URL to the database when a user attempts to unfollow another friend
	 * @return returns the request data and set the status whether the user has been successfully unfollowed or not,
	 * display an error message if exists
	 */
	@RequestMapping(value = "/unfollowFriend", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> unfollowFriend(@RequestBody Map<String, String> params, HttpServletRequest request) { // TESTED AND WORKS

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		String friendUserName = params.get("friendUserName");
		String userName = params.get("userName");

		// Check for BAD_REQUEST
		if (userName == null || friendUserName == null) {
			response.put("status", HttpStatus.BAD_REQUEST);
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}

		// Call the method in profileDriver with the extracted userName and friendUserName
		DbQueryStatus status = profileDriver.unfollowFriend(userName, friendUserName);
		return Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
	}

	/**
	 * This method should be able to identify the song that the user has liked and store it to the database accordingly, this should
	 * also store the songs data in the database as well and be able to store the song in the users playlist
	 *
	 * @param params  this should be able to get the following parameters for this method: userName and songId
	 * @param request sends a request URL to the database when a user likes a song and adds it to their playlist
	 * @return returns the request data and set the status whether the user has been able to successfully like a song
	 * and add it to their playlist or not, display an error message if exists
	 */
	@RequestMapping(value = "/likeSong", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> likeSong(@RequestBody Map<String, String> params, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		String userName = params.get("userName");
		String songId = params.get("songId");

		DbQueryStatus status;

		if (userName != null && songId != null) {
			status = playlistDriver.likeSong(userName, songId);
			Utils.setResponseStatus(response, status.getdbQueryExecResult(), (JSONObject) status.getData());
		} else {
			Utils.setResponseStatus(response, DbQueryExecResult.QUERY_ERROR_GENERIC, null);
			return ResponseEntity.ok(response);
		}
		return Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
	}

	/**
	 * This method should be able to identify the song that the user has chosen to unlike and delete from their playlist, this should
	 * also be able to identify the song that has been selected to be deleted from the users playlist, and you are expected to be
	 * able to delete the songs content and data completely from the database accordingly
	 *
	 * @param params  this should be able to get the following parameters for this method: userName and songId
	 * @param request sends a request URL to the database when a user unlikes a song and deleted it from their playlist
	 * @return returns the request data and set the status whether the user has been able to successfully unlike a song
	 * and remove it from their playlist or not, display an error message if exists
	 */
	@RequestMapping(value = "/unlikeSong", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> unlikeSong(@RequestBody Map<String, String> params, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));

		String userName = params.get("userName");
		String songId = params.get("songId");

		if (userName == null || songId == null) {
			response.put("status", HttpStatus.BAD_REQUEST);
			return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
		}

		DbQueryStatus status = playlistDriver.unlikeSong(userName, songId);

		// Set the response based on the status returned from the unlikeSong method
		return Utils.setResponseStatus(response, status.getdbQueryExecResult(), status.getData());
	}

	/**
	 * This method should be able to allow the user to send a song they find recommending to their friend, this should be able to
	 * get the usernames of both the sender and the receiver and store it to the database accordingly, this method should be able
	 * to properly navigate and identify the song that is being sent from one friend to another
	 *
	 * @param params  this should be able to take and store the following parameters for this method:
	 *                senderUserName, receiverUserName, songId
	 * @param request sends a request URL to the database when a user chooses and sends a song to their friend
	 * @return returns the request data and set the status whether the user has been able to successfully send a song to
	 * their friend or not, display an error message if exists
	 */

	@RequestMapping(value = "/sendSongToFriend", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> sendSongToFriend(@RequestBody Map<String, String> params, HttpServletRequest request) { // VARNI TESTED SEND SONG AND IT WORKS CORRECTLY AS IT SHOULD
		Map<String, Object> response = new HashMap<>();
		String senderUserName = params.get("senderUserName");
		String receiverUserName = params.get("receiverUserName");
		String songId = params.get("songId");


		if (senderUserName == null || receiverUserName == null || songId == null || senderUserName.equals(receiverUserName)) {
			response.put("status", HttpStatus.BAD_REQUEST);
			response.put("message", "Invalid request parameters");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}


		DbQueryStatus dbQueryStatus = profileDriver.sendSongToFriend(senderUserName, receiverUserName, songId);


		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

	}

	/**
	 * This method should be able to allow a user to search for a another username and locate their profile, and allow them to
	 * block whichever user they search for, this is also expected to store everything in the database accordingly
	 *
	 * @param params  this should be able to take and store the following parameters for this method: userName, friendUserName
	 * @param request sends a request URL to the database when a user chooses a profile to block
	 * @return returns the request data and set the status whether the user has been able to successfully navigate a profile
	 * and block them, display an error message if exists.
	 */
	@RequestMapping(value = "/blockFriend", method = RequestMethod.PUT)
	public ResponseEntity<Map<String, Object>> blockFriend(@RequestBody Map<String, String> params, HttpServletRequest request) { // VARNI TESTED BLOCK FRIEND IT WORKS
		Map<String, Object> response = new HashMap<>();
		String userName = params.get("userName");
		String friendUserName = params.get("friendUserName");

		// Check for BAD_REQUEST
		if (userName == null || friendUserName == null || userName.equals(friendUserName)) {
			response.put("status", HttpStatus.BAD_REQUEST);
			response.put("message", "Invalid request parameters");
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}
		DbQueryStatus dbQueryStatus = profileDriver.blockFriend(userName, friendUserName);

		// Response
		response.put("path", String.format("PUT %s", Utils.getUrl(request)));
		response.put("message", dbQueryStatus.getMessage());
		return Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
	}

}