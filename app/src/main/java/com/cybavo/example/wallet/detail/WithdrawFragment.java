/**
 * Copyright (c) 2019 CYBAVO, Inc.
 * https://www.cybavo.com
 *
 * All rights reserved.
 */

package com.cybavo.example.wallet.detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.cybavo.example.wallet.NavFragment;
import com.cybavo.example.wallet.R;
import com.cybavo.example.wallet.helper.CurrencyHelper;
import com.cybavo.example.wallet.helper.Helpers;
import com.cybavo.example.wallet.helper.ToolbarHelper;
import com.cybavo.example.wallet.main.MainViewModel;
import com.cybavo.example.wallet.pincode.InputPinCodeDialog;
import com.cybavo.wallet.service.api.Callback;
import com.cybavo.wallet.service.api.Error;
import com.cybavo.wallet.service.auth.PinSecret;
import com.cybavo.wallet.service.wallet.Currency;
import com.cybavo.wallet.service.wallet.Fee;
import com.cybavo.wallet.service.wallet.Wallet;
import com.cybavo.wallet.service.wallet.Wallets;
import com.cybavo.wallet.service.wallet.results.CreateTransactionResult;
import com.cybavo.wallet.service.wallet.results.EstimateTransactionResult;
import com.cybavo.wallet.service.wallet.results.RequestSecureTokenResult;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

public class WithdrawFragment extends Fragment
        implements InputPinCodeDialog.OnPinCodeInputListener, ConfirmTransactionDialog.OnConfirmListener {

    private static final String ARG_WALLET = "wallet";

    private Wallets mService;
    private Wallet mWallet;
    private MainViewModel mViewModel;
    private WithdrawViewModel mWithdrawViewModel;

    private TextView mCurrency;
    private TextView mBalance;
    private TextView mQuota;
    private TextView mUsage;
    private Spinner mFeeSpinner;
    private Spinner mTokenIdSpinner;
    private View mScanAddress;
    private EditText mAddress;
    private TextView mAmountLabel;
    private EditText mAmount;
    private View mMemoLabel;
    private EditText mMemo;
    private EditText mDescription;
    private Button mSubmit;
    private Button mSubmitWithToken;
    private ProgressBar mLoading;

    private FeeAdapter mFeeAdapter;
    private TokenIdAdapter mTokenIdAdapter;
    private boolean mWithSecureToken = false;
    private boolean mPinForToken = false;

    public WithdrawFragment() {
    }

    public static WithdrawFragment newInstance(Wallet wallet) {
        WithdrawFragment fragment = new WithdrawFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_WALLET, WalletParcelable.fromWallet(wallet));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mWallet = WalletParcelable.toWallet(getArguments().getParcelable(ARG_WALLET));
        }
        mService = Wallets.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_withdraw, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ToolbarHelper.setupToolbar(view, R.id.appBar)
                .title(R.string.title_withdraw)
                .onBack(v -> getFragmentManager().popBackStack())
                .done();

        mCurrency = view.findViewById(R.id.currency);
        mCurrency.setText(mWallet.currencySymbol);
        mCurrency.setCompoundDrawablesWithIntrinsicBounds(
                CurrencyHelper.getCoinIconResource(getContext(), mWallet.currencySymbol), 0, 0, 0);

        mBalance = view.findViewById(R.id.balance);
        mQuota = view.findViewById(R.id.quota);
        mUsage = view.findViewById(R.id.usage);

        mTokenIdSpinner = view.findViewById(R.id.token_id);
        mTokenIdAdapter = new TokenIdAdapter(getContext());
        mTokenIdSpinner.setAdapter(mTokenIdAdapter);

        mFeeSpinner = view.findViewById(R.id.fee);
        mFeeAdapter = new FeeAdapter(getContext());
        mFeeSpinner.setAdapter(mFeeAdapter);

        mScanAddress = view.findViewById(R.id.scanAddress);
        mScanAddress.setOnClickListener(v -> {
            scanAddress();
        });

        mAddress = view.findViewById(R.id.address);
        mAmountLabel = view.findViewById(R.id.amountLabel);
        mAmount = view.findViewById(R.id.amount);
        mMemo = view.findViewById(R.id.memo);
        mMemo.setVisibility(hasMemo() ? View.VISIBLE : View.GONE);
        mMemoLabel = view.findViewById(R.id.memoLabel);
        mMemoLabel.setVisibility(hasMemo() ? View.VISIBLE : View.GONE);

        mDescription = view.findViewById(R.id.description);

        mSubmit = view.findViewById(R.id.submit);
        mSubmit.setOnClickListener(v -> {
            estimateTransaction(false);
        });

        mSubmitWithToken = view.findViewById(R.id.submitWithToken);
        mSubmitWithToken.setOnClickListener(v -> {
            estimateTransaction(true);
        });

        mLoading = view.findViewById(R.id.progress);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mWithdrawViewModel = ViewModelProviders.of(this,
                new WithdrawViewModel.Factory(getActivity().getApplication(), mWallet))
                .get(WithdrawViewModel.class);

        mWithdrawViewModel.getUsage().observe(this, usage -> {
            if (usage == null) { // fetching
                mQuota.setText(getString(R.string.template_quota, "…", mWallet.currencySymbol));
                mUsage.setText(getString(R.string.template_usage, "…", mWallet.currencySymbol));
            } else {
                mQuota.setText(getString(R.string.template_quota, usage.dailyTransactionAmountQuota, mWallet.currencySymbol));
                mUsage.setText(getString(R.string.template_usage, usage.dailyTransactionAmountUsage, mWallet.currencySymbol));
            }
        });

        mWithdrawViewModel.getTransactionFee().observe(this, fees -> {
            mFeeAdapter.clear();
            mFeeAdapter.addAll(fees);
        });

        mViewModel = ViewModelProviders.of(getParentFragment(),
                new MainViewModel.Factory(getActivity().getApplication()))
                .get(MainViewModel.class);
        mViewModel.getBalance(mWallet).observe(this, entry -> {
            if (entry.init) {
                mBalance.setText(getString(R.string.template_amount,
                        CurrencyHelper.getEffectiveBalance(entry.balance), mWallet.currencySymbol));
                if(entry.balance.tokens != null){
                    mTokenIdAdapter.clear();
                    mTokenIdAdapter.addAll(entry.balance.tokens);
                }
            } else {
                mBalance.setText(getString(R.string.template_amount, "…", mWallet.currencySymbol));
            }
        });
        mViewModel.getCurrencies().observe(this, currencies -> {
            final Currency c = CurrencyHelper.findCurrency(currencies, mWallet);
            if (c != null) {
                mCurrency.setText(c.displayName);
                if(CurrencyHelper.isFungibleToken(c)){
                    mTokenIdSpinner.setVisibility(View.VISIBLE);
                    mAmount.setVisibility(View.INVISIBLE);
                    mAmountLabel.setText(R.string.label_token_id);
                }else{
                    mTokenIdSpinner.setVisibility(View.INVISIBLE);
                    mAmount.setVisibility(View.VISIBLE);
                    mAmountLabel.setText(R.string.label_amount);
                }
            } else {
                mCurrency.setText(mWallet.currencySymbol);
            }
        });
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            mAddress.setText(result.getContents());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onPinCodeInput(PinSecret pinSecret) {
        if (mPinForToken) {
            requestSecureToken(pinSecret);
        } else {
            final String toAddress = mAddress.getText().toString();
            final String memo = mMemo.getText().toString();
            final String description = mDescription.getText().toString();
            final Fee fee = (Fee) mFeeSpinner.getSelectedItem();
            String amount = getAmount();
            createTransaction(toAddress, amount, fee, memo, description, pinSecret);
        }
    }

    @Override
    public void onForgotPinCode() {
        NavFragment.find(this).goRestore();
    }

    private void inputPinCode(boolean forToken) {
        mPinForToken = forToken;
        InputPinCodeDialog dialog = InputPinCodeDialog.newInstance();
        dialog.show(getChildFragmentManager(), "pinCode");
    }

    private void scanAddress() {
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.initiateScan();
    }

    private void setInProgress(boolean inProgress) {
        mAddress.setEnabled(!inProgress);
        mScanAddress.setEnabled(!inProgress);
        mAmount.setEnabled(!inProgress);
        mMemo.setEnabled(!inProgress);
        mDescription.setEnabled(!inProgress);
        mFeeSpinner.setEnabled(!inProgress);
        mSubmit.setEnabled(!inProgress);
        mLoading.setVisibility(inProgress ? View.VISIBLE : View.GONE);
    }

    private void createTransaction(String toAddress, String amount, Fee fee, String memo, String description, PinSecret pinSecret) {
        if (toAddress.isEmpty() || amount.isEmpty() || fee == null || pinSecret == null) {
            return;
        }

        setInProgress(true);

        final Map<String, Object> extras = new HashMap<>();
        extras.put("memo", memo);

        mService.createTransaction(mWallet.walletId, toAddress, amount, fee.amount, description, pinSecret, extras,
                new Callback<CreateTransactionResult>() {
                @Override
                public void onError(Throwable error) {
                    mViewModel.getBalance(mWallet, true);
                    Helpers.showToast(getContext(), "createTransaction failed: " + error.getMessage());
                    setInProgress(false);
                }

                @Override
                public void onResult(CreateTransactionResult result) {
                    getFragmentManager().popBackStack();
                    refreshDetailHistory();
                    setInProgress(false);
                }
            });
    }

    private void createTransactionWithSecureToken(String toAddress, String amount, Fee fee, String memo, String description, boolean requestToken) {
        if (toAddress.isEmpty() || amount.isEmpty() || fee == null) {
            return;
        }

        setInProgress(true);

        final Map<String, Object> extras = new HashMap<>();
        extras.put("memo", memo);

        mService.createTransaction(mWallet.walletId, toAddress, amount, fee.amount, description, extras, new Callback<CreateTransactionResult>() {
            @Override
            public void onError(Throwable error) {
                if (requestToken && error instanceof Error && ((Error) error).getCode() == Error.Code.ErrUserSecureTokenNotReady) { // Secure token not ready
                    Helpers.showToast(getContext(), "Secure Token invalid/expired, input PIN code to request a new one");
                    onRequestSecureToken();
                } else {
                    mViewModel.getBalance(mWallet, true);
                    Helpers.showToast(getContext(), "createTransaction failed: " + error.getMessage());
                    setInProgress(false);
                }
            }

            @Override
            public void onResult(CreateTransactionResult result) {
                getFragmentManager().popBackStack();
                refreshDetailHistory();
                setInProgress(false);
            }
        });
    }

    private void onRequestSecureToken() {
        inputPinCode(true);
    }

    private void requestSecureToken(PinSecret pinSecret) {
        final String toAddress = mAddress.getText().toString();
        final String amount = mAmount.getText().toString();
        final String memo = mMemo.getText().toString();
        final String description = mDescription.getText().toString();
        final Fee fee = (Fee) mFeeSpinner.getSelectedItem();

        setInProgress(true);
        mService.requestSecureToken(pinSecret, new Callback<RequestSecureTokenResult>() {
            @Override
            public void onError(Throwable error) {
                Helpers.showToast(getContext(), "requestSecureToken failed: " + error.getMessage());
                setInProgress(false);
            }

            @Override
            public void onResult(RequestSecureTokenResult requestSecureTokenResult) { // retry transaction
                createTransactionWithSecureToken(toAddress, amount, fee, memo, description, false);
            }
        });
    }

    private void refreshDetailHistory() {
        WalletDetailFragment wdf = NavFragment.find(this).findFragment(WalletDetailFragment.class);
        if (wdf != null) {
            wdf.refresh();
        }
    }

    private boolean hasMemo() {
        return mWallet.currency == CurrencyHelper.Coin.EOS ||
                mWallet.currency == CurrencyHelper.Coin.XRP;
    }

    private void estimateTransaction(boolean withSecureToken) {
        final String toAddress = mAddress.getText().toString();
        final Fee fee = (Fee) mFeeSpinner.getSelectedItem();
        String amount = getAmount();
        if (toAddress.isEmpty() || amount.isEmpty() || fee == null) {
            return;
        }

        setInProgress(true);
        mService.estimateTransaction(mWallet.currency, mWallet.tokenAddress, amount, fee.amount, new Callback<EstimateTransactionResult>() {
            @Override
            public void onError(Throwable error) {
                Helpers.showToast(getContext(), "estimateTransaction failed: " + error.getMessage());
                setInProgress(false);
            }

            @Override
            public void onResult(EstimateTransactionResult result) {
                setInProgress(false);
                confirmTransaction(withSecureToken, toAddress, result.tranasctionAmout, result.platformFee, result.blockchainFee);
            }
        });
    }

    private void confirmTransaction(boolean withSecureToken, String address, String transactionAmount, String platformFee, String blockchainFee) {
        final String tag = ConfirmTransactionDialog.class.getSimpleName();
        if (getChildFragmentManager().findFragmentByTag(tag) == null) {
            ConfirmTransactionDialog.newInstance(
                    address, transactionAmount, platformFee, blockchainFee)
                    .show(getChildFragmentManager(), tag);
        }
        mWithSecureToken = withSecureToken;
    }
    private String getAmount(){
        final String amountText = mAmount.getText().toString();
        final String tokenId = (String) mTokenIdSpinner.getSelectedItem();
        String amount = mTokenIdSpinner.getVisibility() == View.VISIBLE ? tokenId : amountText;
        return amount;
    }
    @Override
    public void onConfirm() {
        if (mWithSecureToken) {
            final String toAddress = mAddress.getText().toString();
            final String memo = mMemo.getText().toString();
            final String description = mDescription.getText().toString();
            String amount = getAmount();
            final Fee fee = (Fee) mFeeSpinner.getSelectedItem();
            createTransactionWithSecureToken(toAddress, amount, fee, memo, description, true);
        } else {
            inputPinCode(false);
        }
    }
}
