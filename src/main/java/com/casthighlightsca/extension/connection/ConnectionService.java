package com.casthighlightsca.extension.connection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import okhttp3.OkHttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Date;

public class ConnectionService {

    public static boolean Connect(String id, String password, String token, String url) {

        /* Two connection type ID&Password or Token */
        OkHttpClient client = new OkHttpClient();

        /* Creating group ID for notification */
        String scaExtensionGroupId = NotificationGroup.createIdWithTitle("com.casthighlightsca.extension", "IntelliJ SCA extension plugin");

        /* Parameters to save in final */
        String expiration = "";
        String companyId = "";
        String tokenReceived = "";

        if(token != null && !token.isEmpty()){

            /* ###################################################
               ############### TOKEN Connection ##################
               ###################################################*/

            /* REQUEST */

            // TODO ADD URL BUILDER CHECK FOR DOUBLE SLASH IN URL

            try {
                /* Check that the last char is a slash */
                String lastChar = url.substring(url.length() -1);
                if(!lastChar.equals("/")){
                    url += "/";
                }

                URL obj = new URL(url + "WS2/OAuthService/currentCompanyId");
                HttpURLConnection connectionRequest = (HttpURLConnection) obj.openConnection();

                connectionRequest.setRequestMethod("GET");
                connectionRequest.setRequestProperty("Authorization", "Bearer " + token);
                connectionRequest.setRequestProperty("Accept", "application/json; charset=UTF-8");

                int responseCode = connectionRequest.getResponseCode();

                if(responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connectionRequest.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();

                    JsonElement element = JsonParser.parseString(content.toString());
                    JsonObject json = new JsonObject();

                    if (element.isJsonObject()) {
                        json = element.getAsJsonObject();
                    }

                    expiration = json.get("expiration").getAsString();
                    companyId = json.get("companyId").getAsString();

                } else {
                    Notifications.Bus.notify(new Notification(scaExtensionGroupId, "Error on connection with error code :" + responseCode,
                            NotificationType.ERROR));
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            /* SESSION VALIDITY CHECK */

            if(Integer.parseInt(expiration) == -1){
                /* Connection established */

                // Saves credentials
                CredentialsManagment credentialsManager = new CredentialsManagment();
                credentialsManager.saveCredentials(url, token, Integer.parseInt(expiration), companyId);

                Notifications.Bus.notify(new Notification(scaExtensionGroupId,
                        "Connection success",
                        NotificationType.INFORMATION));
                return true;
            }else{
                Notifications.Bus.notify(new Notification(scaExtensionGroupId,
                        "Connection refused: Token is expired",
                        NotificationType.ERROR));
                return false;
            }


        }else {

           /* ###################################################
              ############# ID&Password Connection ##############
              ###################################################*/

            /* REQUEST */


            if (id == null || id.isEmpty() || password == null || password.isEmpty()) {
                return false;
            }


            // TODO ADD URL BUILDER CHECK FOR DOUBLE SLASH IN URL

            try {
                String userBasis = id + ":" + password;
                String userBasis64 = Base64.getEncoder().encodeToString(userBasis.getBytes());
                /* Check that the last char is a slash */
                String lastChar = url.substring(url.length() -1);
                if(!lastChar.equals("/")){
                    url += "/";
                }

                URL obj = new URL(url + "auth/oauth/authorize/");
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Basic " + userBasis64);
                con.setRequestProperty("Accept", "application/json; charset=UTF-8");

                int responseCode = con.getResponseCode();

                if(responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();


                    JsonElement element = JsonParser.parseString(content.toString());
                    JsonObject json = new JsonObject();

                    if (element.isJsonObject()) {
                        json = element.getAsJsonObject();
                    }

                    expiration = json.get("expiration").getAsString();
                    companyId = json.get("companyId").getAsString();
                    tokenReceived = json.get("token").getAsString();

                } else {
                    Notifications.Bus.notify(new Notification(scaExtensionGroupId, "Error on connection with error code :" + responseCode,
                            NotificationType.ERROR));
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }



            if (isNotExpired(Integer.parseInt(expiration))) {
                /* Connection established */

                // Saves credentials
                CredentialsManagment credentialsManager = new CredentialsManagment();
                credentialsManager.saveCredentials(url, tokenReceived, Integer.parseInt(expiration), companyId);

                Notifications.Bus.notify(new Notification(scaExtensionGroupId,
                        "Connection success",
                        NotificationType.INFORMATION));

                return true;

            }else{
                Notifications.Bus.notify(new Notification(scaExtensionGroupId,
                        "Connection refused: Token is expired",
                        NotificationType.ERROR));
                return false;
            }

        }

    }

    public static boolean isNotExpired(int expiration) {
        try {
            // Check expiration
            Date currentDate = new Date();
            Date expirationDate = new Date(currentDate.getTime() + (long) expiration * 60 * 1000);
            return currentDate.before(expirationDate);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
