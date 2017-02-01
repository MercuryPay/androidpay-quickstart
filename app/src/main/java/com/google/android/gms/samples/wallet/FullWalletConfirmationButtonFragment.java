/*
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.samples.wallet;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import com.goebl.david.Response;
import com.goebl.david.Webb;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wallet.FullWallet;
import com.google.android.gms.wallet.FullWalletRequest;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.PaymentMethodToken;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This is a fragment that handles the creating and sending of a {@link FullWalletRequest} using
 * {@link Wallet#loadFullWallet(GoogleApiClient, FullWalletRequest, int)}. This fragment renders
 * a button which hides the complexity of managing Google Play Services connection states,
 * creation and sending of requests and handling responses. Applications may use this fragment as
 * a drop in replacement of a confirmation button in case the user has chosen to use Google Wallet.
 */
public class FullWalletConfirmationButtonFragment extends Fragment implements
        OnConnectionFailedListener, OnClickListener, AsyncResponse {

    private static final String TAG = "FullWallet";

    /**
     * Request code used when loading a full wallet. Only use this request code when calling
     * {@link Wallet#loadFullWallet(GoogleApiClient, FullWalletRequest, int)}.
     */
    public static final int REQUEST_CODE_RESOLVE_LOAD_FULL_WALLET = 1004;

    protected GoogleApiClient mGoogleApiClient;
    protected ProgressDialog mProgressDialog;
    protected int mItemId;

    private ItemInfo mItemInfo;
    private Button mConfirmButton;
    private MaskedWallet mMaskedWallet;
    private Intent mActivityLaunchIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivityLaunchIntent = getActivity().getIntent();
        mItemId = mActivityLaunchIntent.getIntExtra(Constants.EXTRA_ITEM_ID, 0);
        mMaskedWallet = mActivityLaunchIntent.getParcelableExtra(Constants.EXTRA_MASKED_WALLET);

        String accountName = getApplication().getAccountName();

        // Set up an API client
        FragmentActivity fragmentActivity = getActivity();

        // [START build_google_api_client]
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .enableAutoManage(fragmentActivity, this /* onConnectionFailedListener */)
                .setAccountName(accountName) // optional
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                        .setEnvironment(Constants.WALLET_ENVIRONMENT)
                        .setTheme(WalletConstants.THEME_LIGHT)
                        .build())
                .build();
        // [END build_google_api_client]
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        initializeProgressDialog();
        View view = inflater.inflate(R.layout.fragment_full_wallet_confirmation_button, container,
                false);
        mItemInfo = Constants.ITEMS_FOR_SALE[mItemId];

        mConfirmButton = (Button) view.findViewById(R.id.button_place_order);
        mConfirmButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        confirmPurchase();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "Google Play Services Error: " + result.getErrorMessage());
        handleError(result.getErrorCode());

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    public BikestoreApplication getApplication() {
        return (BikestoreApplication) getActivity().getApplication();
    }

    // [START on_activity_result]
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mProgressDialog.hide();

        // retrieve the error code, if available
        int errorCode = -1;
        if (data != null) {
            errorCode = data.getIntExtra(WalletConstants.EXTRA_ERROR_CODE, -1);
        }

        switch (requestCode) {
            case REQUEST_CODE_RESOLVE_LOAD_FULL_WALLET:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        if (data != null && data.hasExtra(WalletConstants.EXTRA_FULL_WALLET)) {
                            FullWallet fullWallet = data.getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                            // the full wallet can now be used to process the customer's payment
                            // send the wallet info up to server to process, and to get the result
                            // for sending a transaction status
                            fetchTransactionStatus(fullWallet);
                        } else if (data != null && data.hasExtra(WalletConstants.EXTRA_MASKED_WALLET)) {
                            // re-launch the activity with new masked wallet information
                            mMaskedWallet =  data.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                            mActivityLaunchIntent.putExtra(Constants.EXTRA_MASKED_WALLET,
                                    mMaskedWallet);
                            startActivity(mActivityLaunchIntent);
                        }
                        break;
                    case Activity.RESULT_CANCELED:
                        // nothing to do here
                        break;
                    default:
                        handleError(errorCode);
                        break;
                }
                break;
        }
    }
    // [END on_activity_result]

    public void updateMaskedWallet(MaskedWallet maskedWallet) {
        mMaskedWallet = maskedWallet;
    }

    /**
     * For unrecoverable Google Wallet errors, send the user back to the checkout page to handle the
     * problem.
     *
     * @param errorCode
     */
    protected void handleUnrecoverableGoogleWalletError(int errorCode) {
        Intent intent = new Intent(getActivity(), CheckoutActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(WalletConstants.EXTRA_ERROR_CODE, errorCode);
        intent.putExtra(Constants.EXTRA_ITEM_ID, mItemId);
        startActivity(intent);
    }

    private void handleError(int errorCode) {
        switch (errorCode) {
            case WalletConstants.ERROR_CODE_SPENDING_LIMIT_EXCEEDED:
                // may be recoverable if the user tries to lower their charge
                // take the user back to the checkout page to try to handle
            case WalletConstants.ERROR_CODE_INVALID_PARAMETERS:
            case WalletConstants.ERROR_CODE_AUTHENTICATION_FAILURE:
            case WalletConstants.ERROR_CODE_BUYER_ACCOUNT_ERROR:
            case WalletConstants.ERROR_CODE_MERCHANT_ACCOUNT_ERROR:
            case WalletConstants.ERROR_CODE_SERVICE_UNAVAILABLE:
            case WalletConstants.ERROR_CODE_UNSUPPORTED_API_VERSION:
            case WalletConstants.ERROR_CODE_UNKNOWN:
            default:
                // unrecoverable error
                // take the user back to the checkout page to handle these errors
                handleUnrecoverableGoogleWalletError(errorCode);
        }
    }

    private void confirmPurchase() {
        getFullWallet();
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    private void getFullWallet() {
        FullWalletRequest fullWalletRequest = WalletUtil.createFullWalletRequest(mItemInfo,
                mMaskedWallet.getGoogleTransactionId());

        // [START load_full_wallet]
        Wallet.Payments.loadFullWallet(mGoogleApiClient, fullWalletRequest,
                REQUEST_CODE_RESOLVE_LOAD_FULL_WALLET);
        // [END load_full_wallet]
    }

    /**
     * Here the client should connect to their server, process the credit card/instrument
     * and get back a status indicating whether charging the card was successful or not
     */
    private void fetchTransactionStatus(FullWallet fullWallet) {
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        // Log payment method token, if it exists. This token will either be a direct integration
        // token or a Vantiv/Stripe token, depending on the method used when making the MaskedWalletRequest
        PaymentMethodToken token = fullWallet.getPaymentMethodToken();
        if (token != null) {
            // getToken returns a JSON object as a String.
            //
            // For a Stripe token, the 'id' field of the object contains the necessary token.
            //
            // For a Direct Integration token, the object will have the following format:
            // {
            //    encryptedMessage: <string,base64>
            //    ephemeralPublicKey: <string,base64>
            //    tag: <string,base64>
            // }
            // See the Android Pay documentation for more information on how to decrypt the token.

            // Pretty-print the token to LogCat (newlines replaced with spaces).
            Log.d(TAG, "PaymentMethodToken:" + token.getToken().replace('\n', ' '));
        }

        // TODO: Send details such as fullWallet.getProxyCard() or fullWallet.getBillingAddress()
        //       to your server and get back success or failure. If you used Stripe for processing,
        //       you can get the token from fullWallet.getPaymentMethodToken()

        VantivIPTxnTask task = new VantivIPTxnTask(fullWallet);
        task.delegate = this;
        task.execute(getString(R.string.vantiv_ipurl), getString(R.string.vantiv_ipauth), token.getToken());
    }

    protected void initializeProgressDialog() {
        mProgressDialog = new ProgressDialog(getActivity());
        mProgressDialog.setMessage(getString(R.string.loading));
        mProgressDialog.setIndeterminate(true);
    }

    @Override
    public void processFinish(JSONObject output, FullWallet fullWallet) {
        Log.d(TAG, "Txn Response: " + output.toString());

        Intent intent = new Intent(getActivity(), OrderCompleteActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.EXTRA_FULL_WALLET, fullWallet);
        startActivity(intent);
    }
}

interface AsyncResponse {
    void processFinish(JSONObject output, FullWallet fullWallet);
}

class VantivIPTxnTask extends AsyncTask<String, Void, JSONObject> {

    private Exception exception;
    private Webb mWebb = Webb.create();
    public AsyncResponse delegate = null;
    private FullWallet fullWallet;
    private String ipAuth;

    public VantivIPTxnTask(FullWallet fullWallet) {
        this.fullWallet = fullWallet;
    }

    protected JSONObject doInBackground(String... data) {
        try {
            return makeVantivIPRequest(data[0], data[1], data[2]);
        } catch (Exception e) {
            this.exception = e;
            return null;
        }
    }

    protected void onPostExecute(JSONObject result) {
        // TODO: check this.exception
        if(this.exception != null){

            return;
        }
        // TODO: do something with the feed
        delegate.processFinish(result, this.fullWallet);
    }

    private JSONObject makeVantivIPRequest(String url, String ipAuth, String registrationId) {

        mWebb.setBaseUri(url);
        //mWebb.setDefaultHeader(Webb.HDR_USER_AGENT, null);
        JSONObject body = new JSONObject();
        try {
            body.put("OperatorID", "TEST");
            body.put("InvoiceNo", "123456");
            body.put("Purchase", "2.20");
            body.put("Tax", "0.00");
            body.put("TokenType", "RegistrationId");
            body.put("RecordNo", registrationId);
            body.put("Frequency", "OneTime");
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Response<JSONObject> response = mWebb.post("/PaymentsAPI/Credit/SaleByRecordNo")
                .header("Content-Type", "application/json")
                .header("Accept", "*/*")
                .header("Authorization", "Basic " + ipAuth)
                .body(body)
                .ensureSuccess()
                .asJsonObject();

        JSONObject apiResult = response.getBody();

        return apiResult;
    }
}
