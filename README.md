# androidpay-quickstart

## Overview
This sample demonstrates basic usage of the Android Pay API.  For more information, visit the following link:

https://developers.google.com/android-pay/android/tutorial

## Requirements
Run the Android SDK Manager (`android sdk`) and ensure that you have the following installed:

  * Android SDK Build-tools 22.0.1
  * Google Play services version 25
  * Android Support Library version 22.2
  * Android Support Repository version 15
  
## Setting up Vantiv IP Integration
In order to run this sample with the processor gateway model you will need to acquire a Cert eProtect PaypageID
and a Cert Integrated Payments Merchant Identifier. Please contact Vantiv to be set up 
(call 866-622-2880 or [Live Chat](https://chat.vantiv.com/Sales/i3Root/chat-session.htm)).

Place these values in `androidpay-quickstart/app/src/main/res/values/ids.xml` for the `vantiv_paypageid` and 
`vantiv_ipauth` ids.

The `vantiv_ipauth` id value must be a base64 encoded string from the Merchant Identifer and web services password
in the form 
```
<merchantidentifer><=optionalnickname>:<webservicespassword>
```
For example: 
```
12345:password
```
should be base64 encoded to
```
MTIzNDU6cGFzc3dvcmQ=
```
and placed in the `vantiv_ipauth` id.

## Running the sample with Vantiv IP Integration
This sample must be run on an Android device with the AndroidPay app and loaded with an AndroidPay supported
credit card. During checkout be sure to check the "Use Vantiv as payment processor" checkbox.

## Generating a Public/Private Key Pair
In order to run this sample with the 'direct integration' model (as opposed to a processor gateway token) 
you will need to generate a public/private key pair and put the base64-encoded
public key into the resources directory.

In order to generate a suitable key, execute the provided `genkey.sh` script.  You should see
some output like this:

```
Private-Key: (256 bit)
priv:
    7c:a5:b4:31:fa:ec:26:88:65:77:76:0d:c8:6f:ec:
    e1:a8:01:1d:4c:ff:04:87:4d:05:ca:a2:a1:30:49:
    2a:09
pub:
    04:d8:54:1d:f6:0d:b8:a6:fd:ca:ff:e9:ef:7d:ed:
    9a:2c:ff:fd:c1:ee:82:e0:6c:09:3d:d8:2f:ef:de:
    72:4e:0b:2a:56:2a:c9:d9:96:59:09:5a:08:ab:09:
    a8:f1:42:49:40:95:e3:3c:0c:69:67:9f:15:86:56:
    49:82:18:69:26
ASN1 OID: prime256v1

...
```

Taking the value of the `pub` key and as $PUBLICKEY, run the following:

```
echo $PUBLICKEY | xxd -r -p | base64
```

Put the result of that commmand into `app/src/main/res/values/ids.xml`:

```
<resources>
    <string name="public_key">REPLACE_ME</string>

    ...

</resources>
```

The `genkey.sh` script uses `openssl` to generate the key and generates a `.pem` file containing
the key for future reference. Consult the script for more details on how the `openssl` command is
invoked.
