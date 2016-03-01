from subprocess import call
import paho.mqtt.client as mqtt
import time

def on_connect(client, userdata, flags, rc) :
    if rc == 0:
        print "Connected with code: " + str(rc)

        #make sure to renew subscription at every new connection
        client.publish("smartstb/events",'{"event":"play","ts":"2016-02-17T01:40:03+0000"}')
        print "sent start"
        time.sleep(7)
        client.publish("smartstb/events",'{"event":"stop","ts":"2016-02-17T01:40:10+0000"}')
        print "sent stop"
        #client.subscribe("smartstb/events")
    else:
        raise Exception

# Listen to PUBLISH messages received by server
def on_message(client, userdata, msg):
    print (msg.topic + " " + str(msg.payload))


client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

client.connect('127.0.0.1', 1883, 60)
client.loop_forever()

