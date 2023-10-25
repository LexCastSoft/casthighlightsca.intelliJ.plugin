package com.casthighlightsca.extension.connection;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;


public class CredentialsManagment {



    public void saveCredentials(String url, String token, int expiration, String companyId) {
        CredentialAttributes credentialAttributes = new CredentialAttributes("connectionDataSCA");
        String connectionDataSafe = url + "," + token + "," + expiration + "," + companyId;
        Credentials credentials = new Credentials(connectionDataSafe, "");
        PasswordSafe.getInstance().set(credentialAttributes, credentials);
    }

    public void deleteCredentials(){
        CredentialAttributes credentialAttributes = new CredentialAttributes("connectionDataSCA");
        Credentials credentials = new Credentials("", "");
        PasswordSafe.getInstance().set(credentialAttributes, credentials);
    }

    public CredentialsObject loadCredentials() {

        String key = null; // e.g. serverURL, accountID
        CredentialAttributes credentialAttributes = new CredentialAttributes("connectionDataSCA");

        Credentials credentials = PasswordSafe.getInstance().get(credentialAttributes);
        if (credentials == null || credentials.getUserName() == null || credentials.getUserName().isEmpty()) {return null;}
        String concatenedData = credentials.getUserName();

        assert concatenedData != null;
        String[] paramsArray = concatenedData.split(",");

        String url = paramsArray[0];
        String token = paramsArray[1];
        String expiration = paramsArray[2];
        String companyId = paramsArray[3];

        return new CredentialsObject(url, token, Integer.parseInt(expiration), companyId);
    }

}
