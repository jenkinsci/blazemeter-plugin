package hudson.plugins.blazemeter;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.common.collect.LinkedHashMultimap;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.plugins.blazemeter.api.BlazemeterApi;
import hudson.plugins.blazemeter.api.BlazemeterApiV3Impl;
import hudson.plugins.blazemeter.utils.BzmServiceManager;
import hudson.plugins.blazemeter.utils.Constants;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.json.JSONException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

public class BlazeMeterPerformanceBuilderDescriptor extends BuildStepDescriptor<Builder> {

    private String blazeMeterURL=null;
    private String proxyHost=null;
    private String proxyPort=null;
    private String proxyUser=null;
    private String proxyPass=null;
    private String name = "My BlazeMeter Account";
    private static BlazeMeterPerformanceBuilderDescriptor descriptor=null;

    public BlazeMeterPerformanceBuilderDescriptor() {
        super(PerformanceBuilder.class);
        load();
        descriptor=this;
    }

    public static BlazeMeterPerformanceBuilderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "BlazeMeter";
    }

    // Used by config.jelly to display the test list.
    public ListBoxModel doFillTestIdItems(@QueryParameter("jobApiKey") String apiKey,@QueryParameter String apiVersion) throws FormValidation {
        if(apiKey.isEmpty()){
            ListBoxModel keys=getKeys();
            apiKey=keys.get(0).value;

        }
        ListBoxModel items = new ListBoxModel();
        if (apiKey == null) {
            items.add(Constants.NO_API_KEY, "-1");
        } else {
            if(apiKey.contains(Constants.CREDENTIALS_KEY)){
                apiKey=BzmServiceManager.selectUserKeyOnId(this,apiKey);
            }
            BlazemeterApi api = new BlazemeterApiV3Impl(apiKey,this.blazeMeterURL,
                    this.proxyHost,
                    this.proxyPort,
                    this.proxyUser,
                    this.proxyPass);
            try {
                LinkedHashMultimap<String, String> testList = api.getTestsMultiMap();
                if (testList == null){
                    items.add("Invalid API key ", "-1");
                } else if (testList.isEmpty()){
                    items.add("No tests", "-1");
                } else {
                    Set set = testList.entries();
                    for (Object test : set) {
                        Map.Entry me = (Map.Entry) test;
                        items.add(new ListBoxModel.Option(String.valueOf(me.getValue())+"->"+me.getKey(), String.valueOf(me.getValue())));
                    }
                }
            } catch (Exception e) {
                throw FormValidation.error(e.getMessage(), e);
            }
        }
        return items;
    }

    public ListBoxModel doFillJobApiKeyItems(@QueryParameter String jobApiKey) {
        ListBoxModel items = getKeys();

        Iterator<ListBoxModel.Option> iterator=items.iterator();
        while(iterator.hasNext()){
            ListBoxModel.Option option=iterator.next();
            try{
                option.selected=jobApiKey.substring(jobApiKey.length()-4).equals(option.value.substring(option.value.length()-4))?true:false;
            }catch (Exception e){
                option.selected=false;
            }
        }
        return items;
    }

    public List<BlazemeterCredential> getCredentials(Object scope) {
        List<BlazemeterCredential> result = new ArrayList<BlazemeterCredential>();
        Set<String> apiKeys = new HashSet<String>();

        Item item = scope instanceof Item ? (Item) scope : null;
        for (BlazemeterCredential c : CredentialsProvider
                .lookupCredentials(BlazemeterCredential.class, item, ACL.SYSTEM)) {
            String id = c.getId();
            if (!apiKeys.contains(id)) {
                result.add(c);
                apiKeys.add(id);
            }
        }
        return result;
    }

    // Used by global.jelly to authenticate User key
    public FormValidation doTestConnection(@QueryParameter("apiKey") final String userKey)
            throws MessagingException, IOException, JSONException, ServletException {
        return BzmServiceManager.validateUserKey(userKey,this.blazeMeterURL,
                this.proxyHost,
                this.proxyPort,
                this.proxyUser,
                this.proxyPass);
    }

    public FormValidation doTestProxy(@QueryParameter("blazeMeterURL") final String url,
                                      @QueryParameter("proxyHost") final String proxyHost,
                                      @QueryParameter("proxyPort") final String proxyPort,
                                      @QueryParameter("proxyUser") final String proxyUser,
                                      @QueryParameter("proxyPass") final String proxyPass)
            throws MessagingException, IOException, JSONException, ServletException {
        BlazemeterApi api = new BlazemeterApiV3Impl("",url,proxyHost,proxyPort,proxyUser,proxyPass);
        FormValidation f= null;
        try {
            f = api.ping()? FormValidation.okWithMarkup("Server is available with " +
                    "current proxy settings"):
                    FormValidation.error("Error while validating proxy settings");
        } catch (Exception e) {
            e.printStackTrace();
            f=FormValidation.error("Error while validating proxy settings");
        }
        return f;
    }

    public ListBoxModel getKeys(){
        ListBoxModel items = new ListBoxModel();
        Set<String> apiKeys = new HashSet<String>();

        Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);
        for (BlazemeterCredential c : CredentialsProvider
                .lookupCredentials(BlazemeterCredential.class, item, ACL.SYSTEM)) {
            String id = c.getId();
            if (!apiKeys.contains(id)) {
                items.add(new ListBoxModel.Option(c.getDescription(),
                        c.getId(),
                        false));
                apiKeys.add(id);
            }
        }
        return items;
    }


    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        String blazeMeterURL = formData.optString("blazeMeterURL");
        this.blazeMeterURL=blazeMeterURL.isEmpty()?Constants.A_BLAZEMETER_COM:blazeMeterURL;
        this.proxyHost=formData.optString("proxyHost");
        this.proxyPort=formData.optString("proxyPort");
        this.proxyUser=formData.optString("proxyUser");
        this.proxyPass=formData.optString("proxyPass");
        save();
        return true;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBlazeMeterURL() {
        return blazeMeterURL;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public String getProxyPass() {
        return proxyPass;
    }


    public void setBlazeMeterURL(String blazeMeterURL) {
        this.blazeMeterURL = blazeMeterURL;
    }

}

