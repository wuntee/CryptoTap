CryptoTap
=========

___Note: This has been developed against Java 1.6, and must be used to run the application. It uses specific APIs that have changed in different releases of Java. This will only work with Java 1.6___

Purpose
-------
When reverse engineering Java applications, it has become a huge hassle to deal with obfuscation. One major function that I typically want to understand is encryption. Java provides a standard mechanism to perform encryption. Java also has a standard debugging protocol, with an associated API. Marrying those two concepts, I have created CryptoTap. It has the ability to connect to a Java application - either by launching it directly, connecting to a remote debugger, or attaching to a process - and displaying all of the information that is used in the common Java cryptographic routines (Secret Key, Cipher Type, Initialization Vector, Cipher Text and Plain Text).

Usage
-----
    java -jar CryptoTap.jar (-l|--launch) javaArgs (-r|--remote) hostname:port (-p|--pid) pid (-ls|--ls)
        -l|--launch javaArgs: The full java arugment string as if you were to run a 
            command via 'java ...'
        -r|--remote hostname:port: The hostname and port of the remote java process
        -p|--pid pid: Attach to a java VM process. In order for this to work, the process.
            must be started with the '-agentlib:jdwp=transport=dt_socket,server=y' arguments. 
            The PID of the java process will then be what is passed as the argument to --pid.
        -ls|--ls: Flag that will cause the applicaiton to list all available classes and exit.
	
Sample Output
-------------
    $ java -jar CryptoTap.jar -l '-cp CryptoTap.jar com.wuntee.ct.test.AesEncryptDecrypt'
    Secret key:     AES[-65, -45, -15, -41, -68, -33, -124, -44, 106, -115, 92, 107, -118, 85, 97, 35]
    Cipher type:    AES/CBC/PKCS5Padding
    IV:             [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    IV:             [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    Decrypting:     [-47, 55, 32, 84, -114, 15, -71, 4, -75, 2, 119, 126, 85, 96, 70, -43, 36, 25, 51, -49, 29, -54, -104, -79, -17, -92, 2, -125, -127, 15, -43, -76]
    Decrypted:      wuntee is pretty cool
    Encrypting:     this is another string that will be encrypted
    Encrypted:      [-96, 106, -61, 22, 95, -2, 33, 52, -67, -76, -57, -65, 15, -45, -27, -24, 3, -120, 109, 21, -24, -72, -15, 20, 23, -94, -117, -8, -7, 101, -41, -89, 122, -45, 11, 100, 21, -119, 91, 84, 76, -98, 56, -24, 38, -37, -117, -68]
    The debugger has been disconnected.
