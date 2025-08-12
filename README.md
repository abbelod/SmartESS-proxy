# SmartESS-proxy
SmartESS (PowMr) to MQTT proxy - Getting real time data from Inverter that use SmartESS cloud

Aim of the project is to adopt PowMr WiFi Plug Pro to send data over MQTT to HASS beside SmartESS cloud.
It is achived by running SmartESS-proxy and poisoning DNS ess.eybond.com to point to the proxy.

This project requires a running MQTT server like Mosquitto in your network.
This project also requires that you poison the DNS ess.eybond.com to point to this proxy server. You can poison DNS by setting up your own DNS server (like Pi-hole) or adding a custom DNS entry in your router (if your router supports it). 

Currently this program acts as real modbus server and your inverter will connect to it. Additionally it will also act as a fake modbus client and will connect to the real ess.eybond.com ( the ip of ess.eybond.com is hardcoded in the application and you may change it if required ). Your inverter will send all the data to this proxy server. This proxy server will publish the data over mqtt so you can access it on Home Assistant, This Proxy will then relay the data to real ess.eybond.com so you can access and use the app SmartEss too.

Change parameters in conf.ini if needed
```
fakeClient=true
mqttServer=172.16.2.1
mqttPort=1883
enableMqttAuth=false
mqttUser=
mqttPass=
mqttTopic=paxyhome/Inverter/
updateFrequency=10
 ```
 
 Compile Project with:
 ```
 javac -cp .:org.eclipse.paho.client.mqttv3-1.2.5.jar *.java
 ```

Run the project with:
```
java -cp .:org.eclipse.paho.client.mqttv3-1.2.5.jar Engine
``` 

