# Mr. Maestro [![Build Status](https://travis-ci.org/neilprosser/mr-maestro.png)](https://travis-ci.org/neilprosser/mr-maestro)

## Intro

Maestro is the main port of call for deploying applications to AWS. It will kick off each stage of the deployment and track the resulting tasks which describe the progress of the deployment.

You can see @neilprosser talking about the application in [this talk at Clojure eXchange 2014](https://skillsmatter.com/skillscasts/6057-herding-cattle-with-clojure-at-mixradio).

## Running

```
lein run
```

or:

```
lein uberjar
java -jar target/maestro.jar
```

## Configuration

There are a number of properties which are present in the `project.clj`. With the `lein run` option you can just amend the properties and they'll be made available to the application via [lein-environ](https://github.com/weavejester/environ). If using the `uberjar` option, you'll want to `export` them first:

```
export ELASTICSEARCH_URL=http://elasticsearch:9200
# The above property will be recognised by environ as :elasticsearch-url
java -jar maestro.jar
```

## Resources

`GET /ping` - `pong`

`GET /healthcheck` - Shows the health of the application, will give a `200` response code if everything is healthy, otherwise `500`

`GET /queue-status` - Shows the status of the Redis message-queue

`GET /lock` - A `200` if Maestro is locked, a `404` if it is unlocked

`POST /lock` - Lock Maestro

`DELETE /lock` - Unlock Maestro

`GET /deployments` - Query deployments, allowed query-parameters are:

* `application` - the application to filter by
* `environment` - the environment to filter by
* `from` - the number of deployments to start retrieving from (default: `0`)
* `full` - whether to retrieve complete deployment information (default: `false`)
* `region` - limit deployments to this region
* `size` - the number of deployments to retrieve (default: `10`)
* `start-from` - the lower bound of a date filter on deployment start time
* `start-to` - the upper bound of a date filter on deployment start time
* `status` - limit deployments to ones having this status

`GET /deployments/:deployment-id` - The details of a specific deployment

`DELETE /deployments/:deployment-id` - Delete a specific deployment

`GET /deployments/:deployment-id/tasks` - Shows the tasks from a specific deployment

`GET /deployments/:deployment-id/logs` - Shows the logs from a specific deployment

`GET /applications` - The list of all applications known about by Lister

`GET /applications/:application` - The details of a specific application

`PUT /applications/:application` - Upsert an application to Lister, Pedantic and Tyrant. **An email query-string parameter must also be provided**

`POST /applications/:application/:environment/deploy` - Begin the deployment of an application to an environment, JSON body parameters are as follows:

* `ami` - The image to use for deployment
* `hash` - The Tyrant hash to use (if not provided, the latest hash will be used)
* `message` - A message which describes why the deployment is happening
* `silent` - Whether the deployment should generate notification messages (default: `false`)
* `user` - The user who is making the deployment

`POST /applications/:application/:environment/undo` - Begin an undo of an application, in an environment (an undo is considered to be the replacement of a currently failed deployment with whatever was there previously). For this to work, a deployment of the application in that environment must already be in-progress and paused (or stopped due to failure), JSON body parameters are as follows:

* `message` - A message which describes why the undo is happening
* `silent` - Whether the undo should generate notification messages (default: `false`)
* `user` - The user who is carrying out the undo

`POST /applications/:application/:environment/rollback` - Begin a rollback of an application in an environment (a rollback is considered to be a deployment using the Tyrant hash and image of the penultimate completed deployment for the application and environment). JSON body must include the following:

* `message` - a message which describes why the deployment is happening
* `silent` - Whether the rollback should generate notification messages (default: `false`)
* `user` - the user who is making the deployment

`POST /applications/:application/:environment/pause` - Register to pause an ongoing deployment of an application in an environment. The pause will apply the next time the deployment changes actions

`DELETE /application/:application/:environment/pause` - Remove a pause registration for an deployment of the application in an environment

`POST /applications/:application/:environment/resume` - Resume the deployment of an application in an environment

`GET /environments` - All environments in JSON form

`GET /in-progress` - All deployments which are currently in-progress

`DELETE /in-progress/:application/:environment` - Delete a deployment lock for an application and environment

`GET /paused` - All deployments which are currently paused

`GET /awaiting-pause` - All deployments which are currently awaiting a pause

`GET /describe-instances/:application/:environment` - List the instances which are present for the application in the environment. By default the response will be JSON but using `Accept: text/plain` will switch the output to plain-text

## License

Copyright © 2014 MixRadio

[mr-maestro is released under the 3-clause license ("New BSD License" or "Modified BSD License")](https://github.com/mixradio/mr-maestro/blob/master/LICENSE)
