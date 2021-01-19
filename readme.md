# RSocket based RPC Framework

## basic

1. communicate base on RSocket (current use version 1.1.0)
2. serialization use protostuff
3. use JDK proxy to easy wrap interfaces to RPC service.

## core ability

1. binary transmit with protostuff: smaller and faster.
2. auto service register via simple api.
3. auto routeing is supported.
4. totally isolation with RSocket Implement, so Maybe it could keep a good & stable API.
5. a Result object will take any exception from Service to Client side.
6. a routeing trace is included inside request meta, and will be returned with response.

## notes & limitations

1. HAVEN'T support Stream and Channel operation. current is only FNF with RR implement exists.

2. As using of protostuff, there should not contain ANY class not exists both Client and Service in ANY parameter or
   result or exception.
3. If there is some Interface used between Service and Client without same Implement, you may use a JDK Proxy (called as
   Delegator) to transmit data.

## warning

1. this project is on some early stage. should use carefully and on your own risk.
2. any suggestions and help are welcomed.
3. as this is one of mine party time project, some slice of those codes maybe or been used in some commercial projects.
   so this repository will and never mixed into codes in some commercial project, those codes should keep this rights as
   current announced.