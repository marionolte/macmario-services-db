# macmario-services-db
MH Service DB

## CLI Usage

```
java -jar macmario-services-db-1.0.jar 
```


### Commands

```
  set <key>=<value>                                        Set the value of an existing configuration key.
  get <key>                                               Print the value of a configuration key.
  list [prefix]                                           List all keys (optionally filtered by prefix, e.g. list db).
  add <key>=<value>;<desc>;<category>;<type>;<required>   Add a new configuration key. Type: STRING|INTEGER|BOOLEAN|URL|PASSWORD. Required: true|false.
  del <key>                                               Delete a configuration key.
  app <name>                                              Switch the active application context (must be registered).
  apps                                                    Show all registered applications.
  register <name>                                         Register a new application and seed it with default configuration entries.
  unregister <name>                                       Unregister an application and delete all its configuration entries.
  version                                                 Print version information and exit.
  help [command]                                          Show help for all commands or a specific command.
  quit | q                                                Exit the program.
```
