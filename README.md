# LFTP

描述：以UDP作为传输协议的FTP实现。可实现向服务器中传送大文件且从服务器中下载。

具体要求：

Sending file should use the following format: LFTP lsend myserver mylargefile

Getting file should use the following format: LFTP lget myserver mylargefile

The parameter myserver can be a url address or an IP address.

- LFTP should use UDP as the transport layer protocol.
- LFTP must realize 100% reliability as TCP;
- LFTP must implement flow control function similar as TCP;
- LFTP must implement congestion control function similar as TCP;
- LFTP server side must be able to support multiple clients at the same time;
- LFTP should provide meaningful debug information when programs are executed.
