package com.ziqni;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ziqni.admin.ZiqniAdminSample;
import com.ziqni.admin.sdk.JSON;
import com.ziqni.member.ZiqniMemberSample;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {

            // Step 1: Choose between Admin Sample and Member Sample
            System.out.println("Choose an option:");
            System.out.println("1. Run Admin Sample");
            System.out.println("2. Run Member Sample");
            System.out.println("3. Exit");
            System.out.print("Enter your choice (1 or 2): ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline left-over

            if(choice == 3){
                break;
            }

            String apiKey = requestApiKey(scanner);
            String spaceName = requestSpaceName(scanner);

            if (choice == 1) {
                runAdminSample(apiKey, spaceName);
            } else if (choice == 2) {
                String memberReferenceId = requestMemberReferenceId(scanner);
                runMemberSample(apiKey, memberReferenceId, spaceName);
            } else {
                System.out.println("Invalid choice. Please run the program again.");
            }

            System.out.println("Press 'Esc' to exit, or any other key to continue...");

            // Check if the 'Esc' key is pressed to break out of the loop
            try {
                int key = System.in.read();
                if (key == 27) { // ASCII code for Esc key
                    System.out.println("Exiting program.");
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        scanner.close();
    }

    private static String requestApiKey(Scanner scanner) {
        // Step 2: Prompt for API Key
        System.out.print("Please enter your API key: ");
        final var key = scanner.nextLine();
        if(key.length()<5){
            throw new RuntimeException("API key is too short");
        } else {
            return key;
        }
    }

    private static String requestSpaceName(Scanner scanner) {
        // Additional step: Prompt for Space Name
        System.out.print("Please enter the Space Name: ");
        return scanner.nextLine();
    }

    private static String requestMemberReferenceId(Scanner scanner) {
        // Additional step for Member Sample: Prompt for Member Reference ID
        System.out.print("Please enter the Member Reference ID: ");
        return scanner.nextLine();
    }

    private static void runAdminSample(String apiKey, String spaceName) throws Exception {
        System.out.println("Running Admin Sample...");
        System.out.println(" +++ Using API Key: " + apiKey.substring(0, 5) + "*****");
        System.out.println(" +++ Using Space Name: " + spaceName);
        com.ziqni.member.sdk.JSON.getDefault().getMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        new ZiqniAdminSample(apiKey, spaceName);

    }

    private static void runMemberSample(String apiKey, String spaceName, String memberReferenceId) throws Exception {
        System.out.println("Running Member Sample...");
        System.out.println(" +++ Using API Key: " + apiKey.substring(0, 5) + "*****");
        System.out.println(" +++ Using Space Name: " + spaceName);
        System.out.println(" +++ Using Member Reference ID: " + memberReferenceId);
        com.ziqni.member.sdk.JSON.getDefault().getMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        new ZiqniMemberSample(apiKey, spaceName, memberReferenceId);
    }
}
