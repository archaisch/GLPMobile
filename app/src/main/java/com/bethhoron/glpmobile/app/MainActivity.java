package com.bethhoron.glpmobile.app;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.ListFragment;
import android.os.Bundle;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends ListFragment {

        public PlaceholderFragment() {
        }

        /***
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
        ***/

        @Override
        public void onStart() {
            super.onStart();
            new GetGlpRssFeedTask().execute();
        }

        private String getAndroidPitRssFeed() throws IOException {
            InputStream in = null;
            String rssFeed = null;
            try {
                URL url = new URL("http://www.godlikeproductions.com/feeds/forums/1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                in = conn.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                for (int count; (count = in.read(buffer)) != -1; ) {
                    out.write(buffer, 0, count);
                }
                byte[] response = out.toByteArray();
                rssFeed = new String(response, "UTF-8");
            } finally {
                if (in != null) {
                    in.close();
                }
            }
            return rssFeed;
        }

        private class GetGlpRssFeedTask extends AsyncTask<Void, Void, List<String>> {

            @Override
            protected List<String> doInBackground(Void... voids) {
                List<String> result = null;
                try {
                    String feed = getAndroidPitRssFeed();
                    result = parse(feed);
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }

            private List<String> parse(String rssFeed) throws XmlPullParserException, IOException {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser xpp = factory.newPullParser();
                xpp.setInput(new StringReader(rssFeed));
                xpp.nextTag();
                return readRss(xpp);
            }

            private List<String> readRss(XmlPullParser parser)
                    throws XmlPullParserException, IOException {
                List<String> items = new ArrayList<String>();
                parser.require(XmlPullParser.START_TAG, null, "rss");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String name = parser.getName();
                    if (name.equals("channel")) {
                        items.addAll(readChannel(parser));
                    } else {
                        skip(parser);
                    }
                }
                return items;
            }

            private List<String> readChannel(XmlPullParser parser)
                    throws IOException, XmlPullParserException {
                List<String> items = new ArrayList<String>();
                parser.require(XmlPullParser.START_TAG, null, "channel");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String name = parser.getName();
                    if (name.equals("item")) {
                        items.add(readItem(parser));
                    } else {
                        skip(parser);
                    }
                }
                return items;
            }

            private String readItem(XmlPullParser parser) throws XmlPullParserException, IOException {
                String result = null;
                parser.require(XmlPullParser.START_TAG, null, "item");
                while (parser.next() != XmlPullParser.END_TAG) {
                    if (parser.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String name = parser.getName();
                    if (name.equals("title")) {
                        result = readTitle(parser);
                    }
                    else if (name.equals("dc:creator")) {
                        result = result + " - " + readCreator(parser);
                    }
                    else {
                        skip(parser);
                    }
                }
                return result;
            }

            // Processes title tags in the feed.
            private String readTitle(XmlPullParser parser)
                    throws IOException, XmlPullParserException {
                parser.require(XmlPullParser.START_TAG, null, "title");
                String title = readText(parser);
                parser.require(XmlPullParser.END_TAG, null, "title");
                return title;
            }

            private String readCreator(XmlPullParser parser)
                    throws IOException, XmlPullParserException {
                parser.require(XmlPullParser.START_TAG, null, "dc:creator");
                String creator = readText(parser);
                parser.require(XmlPullParser.END_TAG, null, "dc:creator");
                return creator;
            }

            private String readText(XmlPullParser parser)
                    throws IOException, XmlPullParserException {
                String result = "";
                if (parser.next() == XmlPullParser.TEXT) {
                    result = parser.getText();
                    parser.nextTag();
                }
                return result;
            }

            private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    throw new IllegalStateException();
                }
                int depth = 1;
                while (depth != 0) {
                    switch (parser.next()) {
                        case XmlPullParser.END_TAG:
                            depth--;
                            break;
                        case XmlPullParser.START_TAG:
                            depth++;
                            break;
                    }
                }
            }

            @Override
            protected void onPostExecute(List<String> rssFeed) {
                if (rssFeed != null) {
                    setListAdapter(new ArrayAdapter<String>(
                            getActivity(),
                            android.R.layout.simple_list_item_1,
                            android.R.id.text1,
                            rssFeed));
                }
            }
        }
    }
}
