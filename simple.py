import paho.mqtt.publish as publish
import time

publish.single("smartstb/7dd76a7dedfbe33da2b1e03a28b8647101b96f00b701ae35cb792573a23b5e",'{"event":"play","ts":"2016-02-17T01:40:03+0000"}')
publish.single("smartstb/7dd76a7dedfbe33da2b1e03a28b8647101b96f00b701ae35cb792573a23b5e",'{"event":"play","ts":"2016-02-17T01:40:03+0000"}')
publish.single("smartstb/7dd76a7dedfbe33da2b1e03a28b8647101b96f00b701ae35cb792573a23b5e",'{"event":"play","ts":"2016-02-17T01:40:03+0000"}')
publish.single("smartstb/7dd76a7dedfbe33da2b1e03a28b8647101b96f00b701ae35cb792573a23b5e",'{"event":"play","ts":"2016-02-17T01:40:03+0000"}')
print "sent start"
time.sleep(7)
publish.single("smartstb/7dd76a7dedfbe33da2b1e03a28b8647101b96f00b701ae35cb792573a23b5e",'{"event":"stop","ts":"2016-02-17T01:40:10+0000"}')
publish.single("smartstb/7dd76a7dedfbe33da2b1e03a28b8647101b96f00b701ae35cb792573a23b5e",'{"event":"stop","ts":"2016-02-17T01:40:10+0000"}')
print "sent stop"


