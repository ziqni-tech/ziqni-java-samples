package com.ziqni.member;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ziqni.member.models.MemberTokenRequest;
import com.ziqni.member.models.MemberTokenResponse;
import com.ziqni.member.sdk.JSON;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MemberAccessTokenService {

    public static MemberTokenResponse getToken(String yourMemberId, String apiKey){
        try {
            // Define the URL
            URL url = new URL("https://member-api.ziqni.com/member-token");

            // Open a connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Create the request body
            final var memberTokenRequest = new MemberTokenRequest()
                    .apiKey(apiKey) // Set yourApiKey to the API key you received from Ziqni
                    .member(yourMemberId) // Set yourMemberId to the member ID you want to get a token for
                    .isReferenceId(true) // Set to true if yourMemberId is a reference ID, i.e. not a Ziqni member ID
                    .currencyKey("USD") // Set the currency key for the member
                    .languageKey("en") // Set the language key for the member
                    .expires(3600); // Set the token expiration time in seconds

            // Send request
            final var requestBody = JSON.getDefault().getMapper().writeValueAsBytes(memberTokenRequest);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody;
                os.write(input, 0, input.length);
            }

            connection.disconnect();

            // Read the response
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // Parse JSON response
                return JSON.getDefault().getMapper().readValue(response.toString(), MemberTokenResponse.class);

            } else {
                System.out.println("Request failed with HTTP code: " + status);
                return null;

            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
