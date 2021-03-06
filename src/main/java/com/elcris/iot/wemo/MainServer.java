package com.elcris.iot.wemo;

/**
 * Created by stoffe on 2/5/16.
 */

import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;
import com.pubnub.api.PubnubException;
import org.cybergarage.upnp.*;
import org.cybergarage.upnp.control.ActionListener;
import org.cybergarage.upnp.device.DeviceChangeListener;
import org.cybergarage.upnp.device.NotifyListener;
import org.cybergarage.upnp.event.EventListener;
import org.cybergarage.upnp.ssdp.SSDPPacket;
import org.cybergarage.xml.Node;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

public class MainServer implements AutoCloseable {

    public static final String BELKIN_INSIGHT_DEVICE_TYPE = "urn:Belkin:device:insight:1";
    public static final String BELKIN_CONTROLLEE_DEVICE_TYPE = "urn:Belkin:device:controllee:1";
    public static final String BELKIN_BRIDGE_DEVICE_TYPE = "urn:Belkin:device:bridge:1";

    private static int restartTimeout = 25; //Sec

    private static boolean enablePubNub = true;
    private static String publishKey = "pub";
    private static String subscribeKey = "sub";
    private static String channelName = "stoffe";

    private static String deviceType = null;
    private static String deviceName = null;
    private static String deviceSerial = null;

    private static boolean enableMqtt = true;
    private static String mqttHost = "127.0.0.0";
    private static int mqttPort = 18833;
    private static String mqttTopic = "test/demo";

    private static String tropoToken = null;
    private static String smsMessage = "Enjoy Your movie";
    private static String smsTargets = ""; //Use +1num,+1num,+46num

    private static Device theDevice;

    private static MqttClient mqtt;

    private static ConcurrentHashMap<String,Long> latestTimestamp = new ConcurrentHashMap<String,Long>();

    private static long tryCount = 0;

    static Logger log = null;

    public static void main(String[] args) throws IOException {

        Properties prop = new Properties();
        File f = new File("conf/config.properties");

        //String loglevel = prop.getProperty("loglevel","DEBUG");
        String loglevel = prop.getProperty("loglevel","INFO");
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, loglevel);
        log = LoggerFactory.getLogger(MainServer.class);

        log.info("Properties file 1 = {}",((f == null)?"null":""+f.getAbsolutePath()));

        if( f == null || (!f.exists()) ) {
            final File root = new File(MainServer.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            f = new File(root.getParentFile(), "../conf/config.properties"); //In case ob starting from bin script.
            log.info("Properties file 2 = {}",((f == null)?"null":""+f.getAbsolutePath()));
        }

        if( f != null && f.exists() ) {
            prop.load( new FileReader(f));
            String test = prop.getProperty("stoffe","not loaded");
            restartTimeout = new Integer(prop.getProperty("restartTimeout","20"));
            enablePubNub = new Boolean( prop.getProperty("pubnub","true") );
            publishKey = prop.getProperty("publishKey","not loaded");
            subscribeKey = prop.getProperty("subscribeKey","not loaded");
            channelName = prop.getProperty("channelName","not loaded");
            deviceType = prop.getProperty("deviceType",BELKIN_INSIGHT_DEVICE_TYPE);
            deviceName = prop.getProperty("deviceName");
            deviceSerial = prop.getProperty("deviceSerial");
            enableMqtt = new Boolean( prop.getProperty("mqtt","true") );
            mqttHost = prop.getProperty("mqttHost","127.0.0.1");
            mqttPort = new Integer(prop.getProperty("mqttPort","1883"));
            mqttTopic = prop.getProperty("mqttTopic","smartstb");
            smsMessage = prop.getProperty("smsMessage","Like your movie? More at http://imdb.com");
            smsTargets = prop.getProperty("smsTargets",null);
            tropoToken = prop.getProperty("tropoToken",null);

            //Override by System.prop
            //TODO
//            restartTimeout = new Integer(System.getenv("RESTART_TIMEOUT",""+restartTimeout));
//            enablePubNub = new Boolean(System.getenv("ENABLE_PUBNUB",""+enablePubNub) );
//            enableMqtt = new Boolean( System.getenv("ENABLE_MQTT",""+enableMqtt) );
//            mqttPort = new Integer(System.getenv("MQTT_PORT",""+mqttPort));
//            mqttTopic = System.getenv("MQTT_TOPIC",mqttTopic);

            //RESIN DEPLOY
            String resinID = System.getenv("RESIN_DEVICE_UUID");
            if( resinID != null && resinID.length() > 0 ) {
                //restart = 25 default ...
                Map<String,String > envs = System.getenv();
                for ( Map.Entry<String,String> env : envs.entrySet() ) {
                    if( "PUBNUB_PUBKEY".equals(env.getKey()) ){
                        publishKey = env.getValue();
                    } else if( "PUBNUB_SUBKEY".equals(env.getKey()) ){
                        subscribeKey = env.getValue();
                    } else if( "SMS_MESSAGE".equals(env.getKey()) ){
                        smsMessage = env.getValue();
                    } else if( "SMS_TARGETS".equals(env.getKey()) ){
                        smsTargets = env.getValue();
                    } else if( "TROPO_TOKEN".equals(env.getKey()) ){
                        tropoToken = env.getValue();
                    } else if( "DEVICE_TYPE".equals(env.getKey()) ){
                        deviceType = env.getValue();
                    } else if( "DEVICE_NAME".equals(env.getKey()) ){
                        deviceName = env.getValue();
                    } else if( "DEVICE_SERIAL".equals(env.getKey()) ){
                        deviceSerial = env.getValue();
                    } else if( "MQTT_HOST".equals(env.getKey()) ){
                        mqttHost = env.getValue();
                    }
                }

                if( publishKey != null && subscribeKey != null && publishKey.length() > 0 && subscribeKey.length() > 0) {
                    enablePubNub = true;
                    channelName = resinID;
                }
            }

            log.info("Properties loaded = {}",test);
            log.info("Properties restartTimeout = {}",restartTimeout);
            log.info("Properties enablePubNub = {}",enablePubNub);
            log.info("Properties publishKey = {}",publishKey);
            log.info("Properties subscribeKey = {}",subscribeKey);
            log.info("Properties channelName = {}",channelName);
            log.info("Properties deviceType = {}",deviceType);
            log.info("Properties deviceName = {}",deviceName);
            log.info("Properties deviceSerial = {}",deviceSerial);
            log.info("Properties enableMqtt = {}",enableMqtt);
            log.info("Properties mqttHost = {}",mqttHost);
            log.info("Properties mqttPort = {}",mqttPort);
            log.info("Properties mqttTopic = {}",mqttTopic);
            log.info("Properties smsMessage = {}",smsMessage);
            log.info("Properties smsTargets = {}",smsTargets);
            log.info("Properties tropoToken = {}",tropoToken);

        } else {
            log.info("Properties not loaded, no config file!");
            return;
        }

        UPnP.setEnable(UPnP.USE_ONLY_IPV4_ADDR);
        //UPnP.setTimeToLive(); /8 ?

        Runnable r = new Runnable() {

            @Override
            public void run() {
                if( theDevice == null ) {
                    ControlPoint controlPoint = new ControlPoint();
                    controlPoint.addEventListener(new EventListener() {
                        @Override
                        public void eventNotifyReceived(String uuid, long seq, String varName, String value) {
                            log.debug("eventNotifyReceived - uuid : {} seq = {} name = {} : {}",uuid,seq,varName,value);
                        }
                    });
                    controlPoint.addNotifyListener(new NotifyListener() {
                        @Override
                        public void deviceNotifyReceived(SSDPPacket ssdpPacket) {
                            log.debug("SSDPPacket : {}",ssdpPacket);
                        }
                    });
                    controlPoint.addDeviceChangeListener(new DeviceChangeListener() {

                        @Override
                        public void deviceAdded(Device device) {
                            printDevice(device);
                            //TODO only focuses on one device @Stoffe
                            //Add more checks
                            //If type or name are set then filter only on specific

//                            if(device.getDeviceType().equals(BELKIN_BRIDGE_DEVICE_TYPE)) {
//                                //Action action = device.getAction("GetDeviceStatus");
//
//                                Service bridgeService = device.getService("urn:Belkin:service:bridge:1");
//
//                                Action action = bridgeService.getAction("GetEndDevicesWithStatus");
//                                action.setArgumentValue("DevUDN","uuid:Bridge-1_0-231426B010001B");
//                                //Action action = device.getAction("GetEndDevices");
//
//                                if(action.postControlAction()) {
//                                    ArgumentList alist = action.getOutputArgumentList();
//                                    for (Object obj : alist) {
//                                        Argument arg = (Argument) obj;
//                                        log.info("Arg name {} : {}", arg.getName(), arg.getValue());
//                                    }
//                                }
//                                log.info("{}",action.getStatus().getDescription());
//
//                                Action setAction = bridgeService.getAction("SetDeviceStatus");
//
//                                Argument status = setAction.getArgument("DeviceStatus");
//
//                                ArgumentList inlist = new ArgumentList();
////                                Node n = new Node("IsGroupAction").set
////                                inlist.add(new Argument("IsGroupAction","NO"));
////                                inlist.add(new Argument("DeviceID","94103EA2B2770076"));
////                                inlist.add(new Argument("CapabilityID","10006"));
////                                inlist.add(new Argument("CapabilityValue","1"));
//                                inlist.getArgument("IsGroupAction").setValue("NO");
//                                inlist.getArgument("DeviceID").setValue("94103EA2B2770076");
//                                inlist.getArgument("CapabilityID").setValue("10006");
//                                inlist.getArgument("CapabilityValue").setValue("1");
//
//                                setAction.setArgumentList(inlist);
//                                log.info("{}",setAction.postControlAction());
//
//                            }


                            if( deviceType != null && (!device.getDeviceType().equals(deviceType)) ) {
                                log.info("Filter on device type -- aborting : {} != {}", deviceType, device.getDeviceType());
                                return;
                            }

                            if( deviceName != null && (!device.getFriendlyName().equals(deviceName)) ) {
                                log.info("Filter on device name -- aborting : {} != {}", deviceName, device.getFriendlyName());
                                return;
                            }

                            if( deviceSerial != null && (!device.getSerialNumber().equals(deviceSerial)) ) {
                                log.info("Filter on device serial # -- aborting : {} != {}", deviceSerial, device.getSerialNumber());
                                return;
                            }

                            if (device.getDeviceType().equals(deviceType)) {
                                theDevice = device;
                            }
                        }

                        @Override
                        public void deviceRemoved(Device device) {

                            log.warn("Device REMOVE = {}", device.toString());
                            theDevice = null;
                            //Send SMS
                            //sendSms("deviceRemoved:"+device.toString(),smsTargets);
                        }
                    });

                    try {
                        boolean start = controlPoint.start(); //TODO wrong net its already started
                        log.info("Starting CP = {} start CNT = {}", start, ++tryCount);
                        //sendSms("Starting -- "+channelName+" CNT = "+tryCount,"+14088136959");
                    } catch (Throwable t) {
                        log.warn("Failed in start CP : ",t);
                    }
                }
            }
        };

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        //executor.scheduleWithFixedDelay(r, 0, restartTimeout, TimeUnit.SECONDS);
        executor.schedule(r,0,TimeUnit.SECONDS);

        //MQTT
        if(enableMqtt) {
            Thread mqttThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    boolean started = false;
                    while(!started) {
                        try {
                            MainServer.connectToMQTT(mqttHost, mqttPort, mqttTopic, channelName);
                            started = true;
                            log.info("Connected to MQTT server");
                        } catch (MqttException e) {
                            log.warn("Failed to detect MQTT server -- sleep 5 seconds",e);
                            try {
                                Thread.currentThread().sleep(5000);
                            } catch (InterruptedException ignore) {
                                log.trace("",ignore);
                            }
                        }
                    }
                }
            });

            mqttThread.start();
        }

        //PubNub
        if( enablePubNub ) {
            final Pubnub pubnub = new Pubnub(publishKey, subscribeKey);

            try {
                pubnub.subscribe(channelName, new Callback() {
                            @Override
                            public void connectCallback(String channel, Object message) {
                                log.debug("CONNECT Callback");
                                JSONObject msg = new JSONObject();
                                try {
                                    msg.put("msg", "starting---stoffe");
                                    pubnub.publish(channel, msg, new Callback() {
                                    });
                                } catch (JSONException e) {
                                    log.warn("",e);
                                }
                            }

                            @Override
                            public void disconnectCallback(String channel, Object message) {
                                log.warn("SUBSCRIBE : DISCONNECT on channel: {} : {} : {}", channel,message.getClass(),
                                        message.toString());
                            }

                            public void reconnectCallback(String channel, Object message) {
                                log.warn("SUBSCRIBE : RECONNECT on channel: {} : {} : {}", channel,message.getClass(),
                                        message.toString());
                            }

                            @Override
                            public void successCallback(String channel, Object message) {
                                log.warn("SUBSCRIBE : {} : {} : {}", channel, message.getClass(), message.toString());
                                try {
                                    JSONObject msg = new JSONObject(message.toString());
                                    handleJsonObj(msg, channel, "PubNub");
                                } catch (JSONException e) {
                                    log.warn("",e);
                                } catch (ParseException e) {
                                    log.warn("",e);
                                }
                            }

                            @Override
                            public void errorCallback(String channel, PubnubError error) {
                                log.error("SUBSCRIBE : ERROR on channel {} : {}", channel, error.toString());
                                pubnub.disconnectAndResubscribe();
                            }
                        }
                );
            } catch (PubnubException e) {
                log.error("",e);
            }
        }

    }

    private static void handleJsonObj(JSONObject msg, String deviceId, String service) throws JSONException, ParseException {

        if (msg.has("event")) {
            String event = msg.getString("event");
            log.info("EVENT : {}",event);
            //Only track start & stop
            if("play".equals(event) || "stop".equals(event)) {
                if(msg.has("ts")) {
                    String ts = msg.getString("ts");
                    SimpleDateFormat datef = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    long tsl = datef.parse(ts).getTime();
                    boolean latestEvent = checkIfLatest(deviceId, tsl);
                    log.info("TIMESTAMP latest : {} TS = {} service : {}", latestEvent, tsl, service);
                    if( ! latestEvent ) return;
                }
            }
            if (theDevice != null) {
                if ("play".equals(event)) {
                    sendSms(smsMessage,smsTargets);
                    lightDevice(theDevice, false);
                }
                else if ("stop".equals(event))
                    lightDevice(theDevice, true);
            }
        }
    }

    private static synchronized boolean checkIfLatest(String deviceId, long timestamp) {
        if( !latestTimestamp.containsKey(deviceId) ) {
            log.trace("First --- == ture");
            latestTimestamp.put(deviceId, timestamp);
            return true; //first one
        }
        long lastTS = latestTimestamp.get(deviceId);
        log.trace("timestamp : {} lastTS : {} timestamp > lastTS : {}",timestamp,lastTS,timestamp > lastTS);
        boolean latest = timestamp > lastTS;
        if( latest ) {
            latestTimestamp.put(deviceId, timestamp);
        }
        return latest;
    }

    private static void lightDevice(Device device, boolean on) {
        //If type or name are set then filter only on specific
        if( deviceType != null && (!device.getDeviceType().equals(deviceType)) ) {
            log.info("Filter on device type -- aborting : {} != {}", deviceType, device.getDeviceType());
            return;
        }

        if( deviceName != null && (!device.getFriendlyName().equals(deviceName)) ) {
            log.info("Filter on device name -- aborting : {} != {}", deviceName, device.getFriendlyName());
            return;
        }

        if( deviceSerial != null && (!device.getSerialNumber().equals(deviceSerial)) ) {
            log.info("Filter on device serial # -- aborting : {} != {}", deviceSerial, device.getSerialNumber());
            return;
        }

        if (device.getDeviceType().equals(BELKIN_BRIDGE_DEVICE_TYPE)) {
            //We have a bridge
            try {
                DeviceList dl =  device.getDeviceList();
                log.debug("DL = {}",dl);
                for(Object obj:dl) {
                    if( obj instanceof Device ) {
                        Device dd = (Device) obj;
                        printDevice(dd);
                    }
                }
                //Action action = device.getAction("GetDeviceStatus");

//                Action action = device.getAction("GetEndDevices");
//
//                log.debug("Action = {}",action);
//                if(action != null) {
//                    action.setArgumentValue("DevUDN","uuid:Bridge-1_0-231426B010001B");
//                    action.setArgumentValue("ReqListType","PAIRED_LIST");
//                    //action.setArgumentValue("DeviceIDs", on ? 1 : 0);
//                    log.debug("Action run = {}", action.postControlAction());
//                    if(action.postControlAction()) {
//                        ArgumentList alist = action.getOutputArgumentList();
//                        for(Object obj:alist) {
//                            Argument arg = (Argument) obj;
//                            log.debug("Arg name {} : {}", arg.getName(), arg.getValue());
//                        }
//                    }
//                }
                Action action = device.getAction("SetDeviceStatus");

                log.debug("Action = {}",action);
                if(action != null) {

                    Argument status = action.getArgument("DeviceStatus");
                    log.debug("status = {}", status);
                    ArgumentList inlist = action.getInputArgumentList();

                    for(Object listobj : inlist) {
                        log.debug("listobj = {}", listobj);
                        Argument arg = (Argument) listobj;
                        log.debug("arg = {}", arg.getName());
                        log.debug("user = {}", arg.getUserData());
                        log.debug("rel = {}", arg.getRelatedStateVariableName());
                        log.debug("node = {}", arg.getArgumentNode());

                        //ArgumentList arglist = new ArgumentList();
                    }
//                    status.getA
//
//                    ArgumentList inlist = new ArgumentList();
//                    inlist.
//                    inlist.set Argument("IsGroupAction").setValue("NO");
//                    inlist.getArgument("DeviceID").setValue("84182600000217E2");
//                    inlist.getArgument("CapabilityID").setValue("10006");
//                    inlist.getArgument("CapabilityValue").setValue("0");
//
//                    action.setArgumentList(inlist);
//
//                    action.setArgumentValue("DeviceStatusList",inlist.toString());
//                    log.debug("Action run = {}", action.postControlAction());
//                    if(action.postControlAction()) {
//                        ArgumentList alist = action.getOutputArgumentList();
//                        for(Object obj:alist) {
//                            Argument arg = (Argument) obj;
//                            log.debug("Arg name {} : {}", arg.getName(), arg.getValue());
//                        }
//                    }
                }
            } catch( Throwable e ) {
                log.error("",e);
            }
        } else if(device.getDeviceType().equals(BELKIN_INSIGHT_DEVICE_TYPE) ||
                device.getDeviceType().equals(BELKIN_CONTROLLEE_DEVICE_TYPE)) {
            String dummy = device.getAbsoluteURL("/foo.xml");
            log.debug("Dummy = {}", dummy);

            Service eventService = device.getService("urn:Belkin:service:basicevent:1");

//            String loc = "http://172.20.10.12:49153/setup.xml";
//            String clean = loc.substring(0,loc.indexOf('/',8));
//            String dummy2 = device.getAbsoluteURL("/bar.xml","","http://172.20.10.12:49153/");


            if( eventService != null ) {

                Action action = eventService.getAction("SetBinaryState");
                if(action != null) {
                    action.setArgumentValue("BinaryState", on ? 1 : 0);
                    log.info("Action = {}", action);
                    log.info("Action run = {}", action.postControlAction());
                } else {
                   log.warn("WARNINI Action = {}", action);
                }
            }
        }
    }

    private static void printDevice( Device device ) {
        log.info("Device ADD = {}", device.toString());
        log.info("Device Type = {}", device.getDeviceType());
        log.info("Device Name = {}", device.getFriendlyName());
        log.info("Device IFADDR = {}", device.getInterfaceAddress());
        log.info("Device Location = {}", device.getLocation());
        log.info("Device Port = {}", device.getHTTPPort());
        log.info("Device SSDP port = {}", device.getSSDPPort());
        log.info("Device Serial number = {}", device.getSerialNumber());
    }

    @Override
    public void close() throws Exception {
        log.info("Close -- called.");
        //
    }

    static void connectToMQTT(String host, int port, String topic, String deviceId) throws MqttException {
        MemoryPersistence persistence = new MemoryPersistence();
        // MqttDefaultFilePersistence persistence = new
        // MqttDefaultFilePersistence(dir);

        log.debug("Mqtt tcp://{}:{} deviceID = {}",host, port,"WeMo"+deviceId);
        mqtt = new MqttClient("tcp://"+host+":"+port, "WeMo"+deviceId, persistence);
        MqttConnectOptions connOpt = new MqttConnectOptions();
        connOpt.setConnectionTimeout(15); //Wait 15 sec
        connOpt.setKeepAliveInterval(5); //Aggressive every 5 sec ping

        mqtt.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("connectionLost : {}", cause.getLocalizedMessage(), cause);
                try {
                    MainServer.connectToMQTT(host,port,topic,deviceId);
                } catch (MqttException e) {
                    log.warn("",e);
                }
            }

            @Override
            public void messageArrived(String topic, final MqttMessage message)
                    throws Exception {
                String deviceId = topic.substring(topic.indexOf('/')+1);

                log.info("Got message = {} for device - {}", message, deviceId);
                try {
                    JSONObject msg = new JSONObject(new String(message.getPayload()));
                    handleJsonObj(msg, deviceId, "Mqtt");

                } catch (JSONException e) {
                    log.warn("",e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                log.info("deliveryComplete() : {}", token);
            }
        });

        IMqttToken tok = null;
        while( tok == null ) {

            try {
                tok = mqtt.connectWithResult(connOpt);
                log.info("MQTT connected : {}", tok);
            }
            catch( MqttException me ) {
                log.warn("Failed to connected -- sleep 3 ", me);
                try {
                    Thread.currentThread().sleep(3000);
                } catch (InterruptedException ignore) {
                    log.trace("", ignore);
                }
            }
        }

        String myTop = topic+'/'+deviceId;
        log.debug("MQTT Topic = {}", myTop);
        mqtt.subscribe(myTop);
    }

    static void sendSms(String message, String target){
        log.debug("In - Send SMS msg = {} -> {}",message,target);
        if( target == null || target.length() == 0 || tropoToken == null ) return;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                CleanupResource cr = new CleanupResource("https://api.tropo.com/1.0/sessions");
                try {

                    JSONObject in = new JSONObject();
                    in.put("token", tropoToken);
                    in.put("addresses", target);
                    in.put("message_body", message);
                    in.put("senderName", "nada");

                    Representation out = cr.post(new JsonRepresentation(in));
                    if( out != null ) {
                        log.info("TROPO = {}", out.getText());
                    }

                } catch (JSONException je) {
                    log.warn("TROPO JSONException = {}", je.getLocalizedMessage());
                } catch (ResourceException re ) {
                    log.warn("TROPO ResourceException = {}", re.getLocalizedMessage());
                } catch (Throwable t) {
                    log.warn("TROPO Throwable = {}", t.getLocalizedMessage());
                } finally {
                    if(cr != null) cr.release();
                }
            }
        };
        new Thread(r).start(); //Run the SMS send in separate thread ...
    }
}
