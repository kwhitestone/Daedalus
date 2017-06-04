package org.itxtech.daedalus.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Question;
import de.measite.minidns.Record;
import de.measite.minidns.source.NetworkDataSource;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.util.DnsServerHelper;
import org.itxtech.daedalus.util.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class DnsTestFragment extends ToolbarFragment {
    private class Type {
        private Record.TYPE type;
        private String name;

        private Type(String name, Record.TYPE type) {
            this.name = name;
            this.type = type;
        }

        private String getName() {
            return name;
        }

        private Record.TYPE getType() {
            return type;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private static Thread mThread = null;
    private static Runnable mRunnable = null;
    private DnsTestHandler mHandler = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dns_test, container, false);

        final TextView textViewTestInfo = (TextView) view.findViewById(R.id.textView_test_info);

        final Spinner spinnerServerChoice = (Spinner) view.findViewById(R.id.spinner_server_choice);
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, DnsServerHelper.getNames(Daedalus.getInstance()));
        spinnerServerChoice.setAdapter(spinnerArrayAdapter);
        spinnerServerChoice.setSelection(DnsServerHelper.getPosition(DnsServerHelper.getPrimary()));

        ArrayList<Type> types = new ArrayList<Type>() {{
            add(new Type("A", Record.TYPE.A));
            add(new Type("NS", Record.TYPE.NS));
            add(new Type("CNAME", Record.TYPE.CNAME));
            add(new Type("SOA", Record.TYPE.SOA));
            add(new Type("PTR", Record.TYPE.PTR));
            add(new Type("MX", Record.TYPE.MX));
            add(new Type("TXT", Record.TYPE.TXT));
            add(new Type("AAAA", Record.TYPE.AAAA));
            add(new Type("SRV", Record.TYPE.SRV));
            add(new Type("OPT", Record.TYPE.OPT));
            add(new Type("DS", Record.TYPE.DS));
            add(new Type("RRSIG", Record.TYPE.RRSIG));
            add(new Type("NSEC", Record.TYPE.NSEC));
            add(new Type("DNSKEY", Record.TYPE.DNSKEY));
            add(new Type("NSEC3", Record.TYPE.NSEC3));
            add(new Type("NSEC3PARAM", Record.TYPE.NSEC3PARAM));
            add(new Type("TLSA", Record.TYPE.TLSA));
            add(new Type("OPENPGPKEY", Record.TYPE.OPENPGPKEY));
            add(new Type("DLV", Record.TYPE.DLV));
        }};

        final Spinner spinnerType = (Spinner) view.findViewById(R.id.spinner_type);
        ArrayAdapter<Type> typeAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, types);
        spinnerType.setAdapter(typeAdapter);

        final AutoCompleteTextView textViewTestUrl = (AutoCompleteTextView) view.findViewById(R.id.autoCompleteTextView_test_url);
        ArrayAdapter autoCompleteArrayAdapter = new ArrayAdapter<>(Daedalus.getInstance(), android.R.layout.simple_list_item_1, Daedalus.DEFAULT_TEST_DOMAINS);
        textViewTestUrl.setAdapter(autoCompleteArrayAdapter);

        mRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String testDomain = textViewTestUrl.getText().toString();
                    if (testDomain.equals("")) {
                        testDomain = Daedalus.DEFAULT_TEST_DOMAINS[0];
                    }
                    StringBuilder testText = new StringBuilder();
                    ArrayList<String> dnsServers = new ArrayList<String>() {{
                        add(DnsServerHelper.getAddressByDescription(Daedalus.getInstance(), spinnerServerChoice.getSelectedItem().toString()));
                        String servers = Daedalus.getPrefs().getString("dns_test_servers", "");
                        if (!servers.equals("")) {
                            addAll(Arrays.asList(servers.split(",")));
                        }
                    }};
                    DNSQuery dnsQuery = new DNSQuery();
                    Record.TYPE type = ((Type) spinnerType.getSelectedItem()).getType();
                    for (String dnsServer : dnsServers) {
                        testText = testServer(dnsQuery, type, dnsServer, testDomain, testText);
                    }
                    mHandler.obtainMessage(DnsTestHandler.MSG_TEST_DONE).sendToTarget();
                } catch (Exception e) {
                    Logger.logException(e);
                }
            }


            private StringBuilder testServer(DNSQuery dnsQuery, Record.TYPE type, String server, String domain, StringBuilder testText) {
                Logger.debug("Testing DNS " + server);
                testText.append(getString(R.string.test_domain)).append(" ").append(domain).append("\n").append(getString(R.string.test_dns_server)).append(" ").append(server);

                mHandler.obtainMessage(DnsTestHandler.MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();

                try {
                    DNSMessage.Builder message = DNSMessage.builder();
                    message.addQuestion(new Question(domain, type));
                    message.setId((new Random()).nextInt());
                    message.setRecursionDesired(true);
                    message.getEdnsBuilder().setUdpPayloadSize(1024).setDnssecOk(false);

                    long startTime = System.currentTimeMillis();
                    DNSMessage responseAMessage = dnsQuery.query(message.build(), InetAddress.getByName(server), 53);
                    long endTime = System.currentTimeMillis();

                    if (responseAMessage.answerSection.size() > 0) {
                        for (Record record : responseAMessage.answerSection) {
                            if (record.getPayload().getType() == type) {
                                testText.append("\n").append(getString(R.string.test_result_resolved)).append(" ").append(record.getPayload().toString());
                            }
                        }
                        testText.append("\n").append(getString(R.string.test_time_used)).append(" ").
                                append(String.valueOf(endTime - startTime)).append(" ms");
                    } else {
                        testText.append("\n").append(getString(R.string.test_failed));
                    }
                } catch (Exception e) {
                    testText.append("\n").append(getString(R.string.test_failed));
                    Logger.logException(e);
                }

                testText.append("\n\n");

                mHandler.obtainMessage(DnsTestHandler.MSG_DISPLAY_STATUS, testText.toString()).sendToTarget();
                return testText;
            }
        };

        final Button startTestBut = (Button) view.findViewById(R.id.button_start_test);
        startTestBut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(v, R.string.notice_start_test, Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
                startTestBut.setVisibility(View.INVISIBLE);

                InputMethodManager imm = (InputMethodManager) Daedalus.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                textViewTestInfo.setText("");

                if (mThread == null) {
                    mThread = new Thread(mRunnable);
                    mThread.start();
                }
            }
        });


        mHandler = new DnsTestHandler();
        mHandler.setViews(startTestBut, textViewTestInfo);

        return view;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_dns_test).setChecked(true);
        toolbar.setTitle(R.string.action_dns_test);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopThread();
        mHandler.removeCallbacks(mRunnable);
        mRunnable = null;
        mHandler.shutdown();
        mHandler = null;
    }

    private static void stopThread() {
        try {
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
        } catch (Exception ignored) {
        }
    }

    private static class DnsTestHandler extends Handler {
        static final int MSG_DISPLAY_STATUS = 0;
        static final int MSG_TEST_DONE = 1;

        private Button startTestBtn = null;
        private TextView textViewTestInfo = null;

        void setViews(Button startTestButton, TextView textViewTestInfo) {
            this.startTestBtn = startTestButton;
            this.textViewTestInfo = textViewTestInfo;
        }

        void shutdown() {
            startTestBtn = null;
            textViewTestInfo = null;
        }

        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MSG_DISPLAY_STATUS:
                    textViewTestInfo.setText((String) msg.obj);
                    break;
                case MSG_TEST_DONE:
                    startTestBtn.setVisibility(View.VISIBLE);
                    stopThread();
                    break;
            }
        }
    }

    private class DNSQuery extends NetworkDataSource {
        public DNSMessage query(DNSMessage message, InetAddress address, int port) throws IOException {
            return queryUdp(message, address, port);
        }
    }
}
