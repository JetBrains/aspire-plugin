# Database Connection

The plugin supports automatic connection to databases. It analyzes connection strings and creates
temporary [data sources](https://www.jetbrains.com/help/rider/Managing_data_sources.html) based on them. You will find
them in the Database tools window. When the Aspire host is stopped, all created data sources are deleted.

Fully supported:

* PostgreSQL
* Microsoft SQL Server

Partially supported:

* MySQL
* MongoDB
* Redis

![Database list](databases.png){ width="450" }

To disable this behavior, go to **Settings | Tools | Aspire** and disable the **Automatically connect to a created
database** option.

<seealso>
  <category ref="ext">
    <a href="https://www.jetbrains.com/help/rider/Relational_Databases.html">Database Tools and SQL</a>
  </category>
</seealso>