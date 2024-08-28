We've implemented the server using five different classes,
two of which are just single-use classes (but turned into classes
for readibility's sake) and three functional classes:

- **WebServer.java**: Which actually runs the server, delegating requests to RequestHandler.
- **RequestHandler.java**: Which recieves individual requests and decides how to handle them.
- **RequestParser.java**: Which parses and generates HTTP requests.
- **ConfigValues.java**: A standardized format to read from the values of config.ini.
- **HttpMessage.java**: A format to store and send HTTP messages.

The design we've chosen to implement is a less-discussed design philosphy, yet one which
should be well known to anyone in the programming field: the "we'll figure it out as we go"
philosophy. Seriously though - we had each class focus on one aspect, and tried to avoid
mixing responsibilities (although there's probably a function or two that we forgot about):
WebServer would generate threads for RequestHandler to deal with, and pretty much nothing else.
RequestHandler would handle the logic side of request handling: a lot of if statements, switches,
error handling, that sort of stuff. It would assume the rest worked fine, but could not make
such assumptions about itself. RequestParser was a mostly static class that assumed the arguments
it were given were valid, and spat out expected results. 