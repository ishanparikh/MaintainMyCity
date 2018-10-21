import sys
import json
import boto3
from boto3.dynamodb.conditions import Key, Attr

def lambda_handler(event, context):
    dynamodb = boto3.resource('dynamodb')
    table = dynamodb.Table('Holes')

    if event["Operation"] == "put":
        return put(event, context, table)
    if event["Operation"] == "remove":
        return remove(event, context, table)
    if event["Operation"] == "fetch":
        return fetch(event, context, table)
    return {
            "statusCode" : 400,
            "body" : json.dumps('Invalid operation')
            }

def fetch(event, context, table):
    response = table.scan()
    return response

def remove(event, context, table):
    response = table.delete_item(
            Key={
                "LatLng" : event["LatLng"]
                }
        )
    return {
            "statusCode" : 200
            }

def put(event, context, table):

    user = event["User"]
    latLng = event["LatLng"]
    date = event["Date"]
    count = 1

    response = query(event, table, latLng)

    if response['Count'] == 0:
        table.put_item(
            Item={
                "Users" : [user],
                "LatLng" : latLng,
                "Date" : date,
                "Count" : 1
            }
        )

    elif user in response["Items"][0]["Users"]:
        return {
                "statusCode" : 400,
                "body" : json.dumps("user already in list")
                }

    else:
        users = response["Items"][0]["Users"] + [user]
        count = response["Items"][0]["Count"] + 1

        table.put_item(
            Item={
                "Users" : users,
                "LatLng" : latLng,
                "Date" : date,
                "Count" : count
            }
        )

    return {
            "statusCode" : 200,
            "body" : json.dumps('Hello from Lambda')
            }


def query(event, table, latlng):
    response = table.query(
        KeyConditionExpression=Key('LatLng').eq(latlng)
        )
    return response

def Main():

    operation = sys.argv[1]
    user = sys.argv[2]
    latlng = sys.argv[3]
    date = int(sys.argv[4])
    
    event = {
        "Operation" : operation,
        "User": user,
        "LatLng": latlng,
        "Date" : date
    }
    context = 0
    lambda_handler(event, context)


if __name__ == '__main__':
    Main()
