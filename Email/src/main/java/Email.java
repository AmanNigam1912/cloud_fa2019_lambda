
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class Email implements RequestHandler<SNSEvent,Object> {

    public Object handleRequest(SNSEvent request, Context context){
        JSONObject obj;
        String message="";
        String to="";
        String HTMLBODY="";
        String domainName = System.getenv("domainName");
        int timeToLive = Integer.parseInt(System.getenv("timeToLive"));
        String FROM = "no-reply@"+domainName;

        String SUBJECT = "Amazon SES test (EMAIL FOR USER)";

        String TEXTBODY = "This email was sent through Amazon SES "
                + "using the AWS SDK for Java.";

        int recipeCount;
        List<String> links = new ArrayList<String>();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Lambda Invocation started: "+ timeStamp);

        try{
            //database check
            AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().withRegion("us-east-1").build();
            DynamoDB dynamoDB = new DynamoDB(dbClient);
            Table table = dynamoDB.getTable("csye6225");
            UUID uuid = UUID.randomUUID();
            String token = uuid.toString();

            context.getLogger().log("Num of records "+request.getRecords().size());

            List<SNSEvent.SNSRecord> lstSnsRecord = request.getRecords();
            for(SNSEvent.SNSRecord record:lstSnsRecord){
                if(record!=null) {
                    context.getLogger().log("SNSRecord found");
                    message = record.getSNS().getMessage();
                    obj = new JSONObject(message);
                    to = obj.getString("0");
                    context.getLogger().log("TO:USER_EMAIL " + to);
                    recipeCount = Integer.parseInt(obj.getString("1"));
                    context.getLogger().log("RECIPE_COUNT " + recipeCount);

                    Date todayCal = Calendar.getInstance().getTime();
                    SimpleDateFormat crunchifyFor = new SimpleDateFormat("MMM dd yyyy HH:mm:ss.SSS zzz");
                    String curTime = crunchifyFor.format(todayCal);
                    Date curDate = crunchifyFor.parse(curTime);
                    Long epoch = curDate.getTime();
                    String currentTs = epoch.toString();
                    context.getLogger().log("Time for resource retrieval " + currentTs);

                    QuerySpec querySpec = new QuerySpec().withKeyConditionExpression("id = :vid").withFilterExpression("ttl_timestamp > :vtimeStamp")
                            .withValueMap(new ValueMap().withString(":vid", to).withString(":vtimeStamp", currentTs));
                    ItemCollection<QueryOutcome> itemcollection = table.query(querySpec);
                    Iterator<Item> iterator = itemcollection.iterator();

                    if (iterator.hasNext() == false) {
                        context.getLogger().log("Entry could not be found for " + to);
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.MINUTE, timeToLive);
                        Date currentDate = cal.getTime();
                        SimpleDateFormat crunchifyFormat = new SimpleDateFormat("MMM dd yyyy HH:mm:ss.SSS zzz");
                        String currentTime = crunchifyFormat.format(currentDate);
                        Date date = crunchifyFormat.parse(currentTime);
                        Long ts = date.getTime();

                        Item item = new Item();
                        item.withPrimaryKey("id", to);
                        item.with("ttl_timestamp", ts.toString());
                        context.getLogger().log("Logging time:" + ts.toString());
                        PutItemOutcome outcome = table.putItem(item);

                        int recipeTracker = 2;
                        while (recipeCount != 0) {
                            links.add("https://" + domainName + "/Recipe_Management_System/v1/recipe/" + obj.getString(String.valueOf(recipeTracker)));
                            recipeCount--;
                            recipeTracker++;
                        }
                        if (links.size() != 0) {
                            HTMLBODY = "<h1>Your recipe links are: </h1>";
                            for (String s : links) {
                                HTMLBODY = HTMLBODY + "<a href=" + s + ">" + s + "</a>" + "<br/>";
                            }
                        }

                        AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard().withRegion("us-east-1").build();
                        SendEmailRequest emailRequest = new SendEmailRequest().withDestination(new Destination().withToAddresses(to))
                                .withMessage(new Message().withBody(new Body().withHtml(new Content()
                                        .withCharset("UTF-8").withData(HTMLBODY)).withText(new Content()
                                        .withCharset("UTF-8").withData(TEXTBODY))).withSubject(new Content()
                                        .withCharset("UTF-8").withData(SUBJECT))).withSource(FROM);
                        client.sendEmail(emailRequest);
                        context.getLogger().log("Email Sent!");
                    }
                    else{
                        Item item=iterator.next();
                        context.getLogger().log("user token found");
                        context.getLogger().log("username:"+item.getString("id"));
                        context.getLogger().log("ttl timestamp:"+item.getString("ttl_timestamp"));
                    }
                }
            }

        }
        catch(Exception e){
            context.getLogger().log("Error message: " + e.getMessage()+"stack: "+e.getStackTrace()[e.getStackTrace().length -1].getLineNumber());
            // context.getLogger().log("Exception: "+e.getMessage());
            //context.getLogger().log(e.getStackTrace()[e.getStackTrace().length -1].getFileName());
        }

        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Lambda Invocation completed: " + timeStamp);

        return null;
    }
}
