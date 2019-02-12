package ml.docilealligator.infinityforreddit;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

class ParseComment {
    interface ParseCommentListener {
        void onParseCommentSuccess(List<?> commentData, String parentId, String commaSeparatedChildren);
        void onParseCommentFailed();
    }

    interface ParseMoreCommentBasicInfoListener {
        void onParseMoreCommentBasicInfoSuccess(String commaSeparatedChildrenId);
        void onParseMoreCommentBasicInfoFailed();
    }

    static void parseComment(String response, ArrayList<CommentData> commentData, Locale locale,
                             boolean isPost, int parentDepth, int childrenStartIndex, ParseCommentListener parseCommentListener) {
        try {
            JSONArray childrenArray = new JSONArray(response);

            if(isPost) {
                childrenArray = childrenArray.getJSONObject(childrenStartIndex).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
            } else {
                childrenArray = childrenArray.getJSONObject(childrenStartIndex).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)
                        .getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getJSONObject(JSONUtils.REPLIES_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
            }
            new ParseCommentAsyncTask(childrenArray, commentData, locale, isPost, parentDepth, childrenStartIndex, parseCommentListener).execute();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.i("comment json error", e.getMessage());
            parseCommentListener.onParseCommentFailed();
        }
    }

    static void parseMoreCommentBasicInfo(String response, ParseMoreCommentBasicInfoListener parseMoreCommentBasicInfoListener) {
        new ParseMoreCommentBasicInfoAsyncTask(response, parseMoreCommentBasicInfoListener).execute();
    }

    static void parseMoreComment(String response, ArrayList<CommentData> commentData, Locale locale,
                                 boolean isPost, int parentDepth, int childrenStartIndex, ParseCommentListener parseCommentListener) {
        try {
            JSONArray childrenArray = new JSONObject(response).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
            new ParseCommentAsyncTask(childrenArray, commentData, locale, isPost, parentDepth, childrenStartIndex, parseCommentListener).execute();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.i("comment json error", e.getMessage());
            parseCommentListener.onParseCommentFailed();
        }
    }

    private static class ParseCommentAsyncTask extends AsyncTask<Void, Void, Void> {
        private JSONArray comments;
        private ArrayList<CommentData> commentData;
        private ArrayList<CommentData> newcommentData;
        private StringBuilder commaSeparatedChildren;
        private Locale locale;
        private boolean isPost;
        private int parentDepth;
        private int childrenStartIndex;
        private ParseCommentListener parseCommentListener;
        private boolean parseFailed;
        private String parentId;

        ParseCommentAsyncTask(JSONArray comments, ArrayList<CommentData> commentData, Locale locale,
                              boolean isPost, int parentDepth, int childrenStartIndex, ParseCommentListener parseCommentListener){
            this.comments = comments;
            this.commentData = commentData;
            newcommentData = new ArrayList<>();
            commaSeparatedChildren = new StringBuilder();
            this.locale = locale;
            this.isPost = isPost;
            this.parentDepth = parentDepth;
            this.childrenStartIndex =  childrenStartIndex;
            parseFailed = false;
            this.parseCommentListener = parseCommentListener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                int actualCommentLength;
                ArrayList<String> children = new ArrayList<>();

                /*if(isPost) {
                    allComments = comments.getJSONObject(childrenStartIndex).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
                } else {
                    allComments = comments.getJSONObject(childrenStartIndex).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)
                            .getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getJSONObject(JSONUtils.REPLIES_KEY)
                            .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
                }*/
                if(comments.length() == 0) {
                    return null;
                }

                JSONObject more = comments.getJSONObject(comments.length() - 1).getJSONObject(JSONUtils.DATA_KEY);

                //Maybe children contain only comments and no more info
                if(more.has(JSONUtils.COUNT_KEY)) {
                    JSONArray childrenArray = more.getJSONArray(JSONUtils.CHILDREN_KEY);

                    parentId = more.getString(JSONUtils.PARENT_ID_KEY);
                    for(int i = 0; i < childrenArray.length(); i++) {
                        children.add(childrenArray.getString(i));
                    }

                    for(String c : children) {
                        commaSeparatedChildren.append(c).append(",");
                    }
                    commaSeparatedChildren.deleteCharAt(commaSeparatedChildren.length() - 1);

                    actualCommentLength = comments.length() - 1;
                } else {
                    actualCommentLength = comments.length();
                }

                for (int i = 0; i < actualCommentLength; i++) {
                    JSONObject data = comments.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
                    String id = data.getString(JSONUtils.ID_KEY);
                    String fullName = data.getString(JSONUtils.LINK_ID_KEY);
                    String author = data.getString(JSONUtils.AUTHOR_KEY);
                    boolean isSubmitter = data.getBoolean(JSONUtils.IS_SUBMITTER_KEY);
                    String commentContent = "";
                    if(!data.isNull(JSONUtils.BODY_HTML_KEY)) {
                        commentContent = data.getString(JSONUtils.BODY_HTML_KEY);
                    }
                    String permalink = data.getString(JSONUtils.PERMALINK_KEY);
                    int score = data.getInt(JSONUtils.SCORE_KEY);
                    long submitTime = data.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;
                    boolean scoreHidden = data.getBoolean(JSONUtils.SCORE_HIDDEN_KEY);

                    Calendar submitTimeCalendar = Calendar.getInstance();
                    submitTimeCalendar.setTimeInMillis(submitTime);
                    String formattedSubmitTime = new SimpleDateFormat("MMM d, YYYY, HH:mm",
                            locale).format(submitTimeCalendar.getTime());

                    int depth;
                    if(data.has(JSONUtils.DEPTH_KEY)) {
                        depth = data.getInt(JSONUtils.DEPTH_KEY) + parentDepth;
                    } else {
                        depth = parentDepth;
                    }
                    boolean collapsed = data.getBoolean(JSONUtils.COLLAPSED_KEY);
                    boolean hasReply = !(data.get(JSONUtils.REPLIES_KEY) instanceof String);

                    newcommentData.add(new CommentData(id, fullName, author, formattedSubmitTime, commentContent, score, isSubmitter, permalink, depth, collapsed, hasReply, scoreHidden));
                }
            } catch (JSONException e) {
                parseFailed = true;
                Log.i("parse comment error", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if(!parseFailed) {
                commentData.addAll(newcommentData);
                parseCommentListener.onParseCommentSuccess(commentData, parentId, commaSeparatedChildren.toString());
            } else {
                parseCommentListener.onParseCommentFailed();
            }
        }
    }

    private static class ParseMoreCommentBasicInfoAsyncTask extends AsyncTask<Void, Void, Void> {
        private JSONArray children;
        private StringBuilder commaSeparatedChildren;
        private ParseMoreCommentBasicInfoListener parseMoreCommentBasicInfoListener;
        private boolean parseFailed;

        ParseMoreCommentBasicInfoAsyncTask(String response, ParseMoreCommentBasicInfoListener parseMoreCommentBasicInfoListener) {
            this.parseMoreCommentBasicInfoListener = parseMoreCommentBasicInfoListener;
            try {
                children = new JSONObject(response).getJSONObject(JSONUtils.JSON_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.THINGS_KEY);
                commaSeparatedChildren = new StringBuilder();
            } catch (JSONException e) {
                parseMoreCommentBasicInfoListener.onParseMoreCommentBasicInfoFailed();
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                for(int i = 0; i < children.length(); i++) {
                    commaSeparatedChildren.append(children.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.ID_KEY));
                    commaSeparatedChildren.append(",");
                }
                commaSeparatedChildren.deleteCharAt(commaSeparatedChildren.length() - 1);
                parseFailed = false;
            } catch (JSONException e) {
                parseMoreCommentBasicInfoListener.onParseMoreCommentBasicInfoFailed();
                parseFailed = true;
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if(!parseFailed) {
                parseMoreCommentBasicInfoListener.onParseMoreCommentBasicInfoSuccess(commaSeparatedChildren.toString());
            } else {
                parseMoreCommentBasicInfoListener.onParseMoreCommentBasicInfoFailed();
            }
        }
    }
}
