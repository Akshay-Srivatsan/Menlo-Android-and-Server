/*
   For step-by-step instructions on connecting your Android application to this backend module,
   see "App Engine Java Servlet Module" template documentation at
   https://github.com/GoogleCloudPlatform/gradle-appengine-templates/tree/master/HelloWorld
*/

package tech.apeiron.menlo.backend;

import com.firebase.client.Firebase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.*;

class FCMData {
    private String message;

    public FCMData() {
        message = "Notification Data";
    }
}

class FCMNotification {
    private String body;
    private String title;
    private String icon;

    public FCMNotification() {
        title = "Menlo School";
        body = "Check out what's new!";
    }

    public FCMNotification(String text) {
        title = "Menlo School";
        body = text;
    }
}

class FCMRequest {
    private String to;
    private FCMData data;
    private FCMNotification notification;
    private String priority;

    public FCMRequest() {
        to = "/topics/developer_override";
        data = new FCMData();
        notification = new FCMNotification();
        priority = "high";
    }


    public FCMRequest(String text, String category) {
        to = "/topics/";
        to += category.replace(' ', '_');
        data = new FCMData();
        notification = new FCMNotification(text);
        priority = "high";
    }
}

public class MenloServlet extends HttpServlet {

    static Logger log = Logger.getLogger("tech.apeiron.menlo.backend.MenloServlet");
    static boolean activated = false;

    @Override
    public void init() {
        if (activated) return;
        activated = true;

        log.info("Hello!");

        log.info(System.getProperty("user.dir"));
        try {
            Map<String, Object> auth = new HashMap<String, Object>();
            auth.put("uid", "notification-service-worker");

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setServiceAccount(new FileInputStream("Menlo-Private.json"))
                    .setDatabaseUrl("https://menlo-ea5c9.firebaseio.com/")
                    .setDatabaseAuthVariableOverride(auth)
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (FileNotFoundException e) {
            log.info("Could not find credentials!");
            e.printStackTrace();
        }


//        Firebase firebase = new Firebase("https://menlo-ea5c9.firebaseio.com/posts");
        DatabaseReference posts = FirebaseDatabase
                .getInstance()
                .getReference("posts").limitToLast(5).getRef();

        posts.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                HttpClient client = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost("https://fcm.googleapis.com/fcm/send");
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setHeader("Authorization", MenloPrivate.authorization);

                Map<String, Object> data = (Map<String, Object>)dataSnapshot.getValue();

                Boolean hasBeenNotified = (Boolean)(dataSnapshot.child("notification").getValue());

                if (hasBeenNotified != null && hasBeenNotified) {
                    return;
                }

                String title = (String)data.get("title");
                String category = (String)data.get("topic");

                Gson g = new Gson();
                String json = g.toJson(new FCMRequest(title, category));
                log.info(json);

                try {
                    HttpEntity entity = new StringEntity(json);
                    httpPost.setEntity(entity);

                    log.info(client.toString());
                    log.info(httpPost.toString());
                    log.info(dataSnapshot.toString());

                    client.execute(httpPost);

                    DatabaseReference currentPost = FirebaseDatabase
                            .getInstance()
                            .getReference("/posts/" + dataSnapshot.getKey() + "/notification");

                    currentPost.setValue(true);


                } catch (IOException e) {
                    log.info("Error!");
                    log.info(e.getMessage());

                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                // No need to notify everyone again.
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                // Not really a point in telling people that a post was deleted.
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                // How would this even happen?
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/plain");
        resp.getWriter().println("Invalid Request.");
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("text/plain");
        resp.getWriter().println("Invalid Request");
    }
}
