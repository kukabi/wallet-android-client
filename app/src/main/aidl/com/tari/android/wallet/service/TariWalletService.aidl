package com.tari.android.wallet.service;

import com.tari.android.wallet.model.Model;

interface TariWalletService {

    void generateTestData();

    BalanceInfo getBalanceInfo();

    List<Contact> getContacts();

}