# Getting Started with the UnboundID SCIM SDK and Reference Implementation #
The UnboundID SCIM Reference Implementation is an easy-to-use, self-contained implementation of a SCIM service provider (server) and consumer (client). The server is built atop the UnboundID In-Memory Directory Server and allows for custom mappings between the LDAP and SCIM data models. The UnboundID SCIM SDK is a pre-packaged collection of libraries and extensible classes that provides developers with a simple, concrete API to interact with a SCIM service provider.  Both the client and server utilize the UnboundID SCIM SDK, UnboundID LDAP SDK for Java, and other open-source libraries.

The reference implementation supports all required aspects of the SCIM API, schema, and schema extension model. The SDK is open-source and freely distributable under the terms of the GPLv2, LGPLv2.1 and UnboundID Free License.

Before starting to use this tool set, you should become familiar with the SCIM Schema, REST API, and related documentation. Understanding the basic concepts will make it much easier to make efficient use of the reference implementation. See http://www.simplecloud.info for more information.

## Downloading and Extracting the Package ##

The SCIM Reference Implementation and SDK can be downloaded from https://www.unboundid.com/resources/scim. The SCIM SDK is also available from the Maven Central repository, using the following dependency in your POM file:

```
<dependency>
  <groupId>com.unboundid.product.scim</groupId>
  <artifactId>scim-sdk</artifactId>
  <version>1.8.2</version>
</dependency>
```

Once you have downloaded the zip, extract it to a clean directory and open the README.txt file to learn about the different components it includes.

## System Requirements ##

The SDK is written in pure Java and is thus deployable on a wide variety of platforms. The following software is required:

  * Java Development Kit 6.0, Standard Edition
  * Apache Maven 3.0.3 or higher

## Starting the Server ##

The server executable is located at server/tools/in-memory-scim-server. An XML file that contains resources definitions and mappings must be specified with the –resourceMappingFile option. This file specifies the SCIM resources that the server will support, and the mappings between SCIM attributes and LDAP attributes. The out-of-the-box configuration (under server/config/resources.xml) provides mappings for the /User and /Group SCIM resources.

The in-memory LDAP server will initially be empty; the –ldifFile option may be used to load an initial set of data from an LDIF file. Some sample user entries are provided in server/ldif/spec-compact.ldif. To start the server with the default configuration, use the following commands:

```
$> cd server

$> tools/in-memory-scim-server –-resourceMappingFile config/resources.xml –-ldifFile ldif/spec-compat.ldif
```

When the server starts successfully, you should see the following:

```
Listening for HTTP client connections on port 8080 and LDAP client connections on port 1389.
```

By default, the server will start listening for HTTP requests on port 8080 and LDAP requests on port 1389. Ports can be changed with the –port and –ldapPort options respectively. Use the –help option for a complete list of options.

## Testing and Verification ##

HTTP Basic authentication is used to authenticate SCIM clients. The credentials are mapped from a SASL Plain Bind request to the in-memory LDAP server. The ‘uid’ and ‘userPassword’ attributes of any user entry can be used for authentication.

If cURL is available, you can retrieve a list of users via the /Users endpoint in JSON format:

```
$> curl http://localhost:8080/Users –user bjensen:password
```

The server generates resource IDs from the DN of the mapped entry. Thus, you can use the DN to retrieve a specific user:

```
$> curl http://localhost:8080/Users/uid=jsmith,dc=example,dc=com –user bjensen:password
```

Similarly, you can create a new resource from a file containing JSON formatted content. For example, create a new Group with the file ‘group.json’ using the POST method as follows:

```
$> curl http://localhost:8080/Groups –user bjensen:password -X POST -d @group.json -H Content-Type:application/json
```

Since all data is backed by the in-memory LDAP server, resources created or modified via the SCIM REST protocol will be lost when the server is restarted. To make persistent changes, make the modifications in the LDIF file, specified by the –ldifFile argument, when starting the server.

## Resource Mapping Configuration ##

The resource configuration XML file, specified with the –resourceMappingFile, defines both the resource endpoints and SCIM schema exposed by the server, as well as how to map those resources to/from LDAP entries.

The top level `<resources>` element configures a SCIM resource endpoint that is exposed by the server. Conventionally, each SCIM resource endpoint is mapped to an LDAP objectClass (i.e. Users to inetOrgPerson, Groups to groupOfUniqueNames) so that there is a one-to-one mapping between a SCIM resource and an LDAP entry. The constructed ID for each SCIM resource is simply the DN of the corresponding LDAP entry.

The `<LDAPSearch>` element describes how a SCIM query is mapped to an LDAP search request. The nested `<baseDN>` element is also used to construct the entry DN when retrieving a known SCIM resource by ID.

The `<LDAPAdd>` element provides information that is needed to create an LDAP entry from a SCIM resource. It defines how to generate the DN via a template and the objectClasses to include.

The `<attribute>` element defines a SCIM attribute for a resource. Unless otherwise specified, all attributes are associated with the core SCIM schema (urn:scim:schemas:core:1.0). Four attribute types may be defined:

  * `<simple>` Represents a singular simple SCIM attribute.
  * `<complex>` Represents a singular complex SCIM attribute. Sub-attributes are defined using the `<subAttribute>` element.
  * `<simpleMultiValued>` Represents a multi-valued simple SCIM attribute. The canonical values allowed may be defined with the `<canonicalValue>` element.
  * `<complexMultiValued>` Represents a multi-valued complex SCIM attribute. The sub-attributes are defined using the `<subAttribute>` element. The canonical values allowed may be defined with the `<canonicalValue>` element.

The XML schema file, server/config/resources.xsd, describes the elements configuration file in detail. Use this file with xmllint to verify the resources.xml configuration file as follows:

```
$> xmllint –noout –schema config/resources.xsd config/resources.xml
```

## Attribute Value Mapping ##

All defined simple attributes and sub-attributes may contain a `<mapping>` element to map their values to a corresponding value from a LDAP attribute. For example, the following lines map the SCIM ‘userName’ attribute to the LDAP ‘uid’ attribute:

```
<attribute name="userName" schema="urn:scim:schemas:core:1.0" readOnly="false" required="true">
  <description>Unique identifier for the User, typically used by
    the user to directly authenticate to the Service Provider</description>
  <simple dataType="string" caseExact="false">
    <mapping ldapAttribute="uid"/>
  </simple>
</attribute>
```

These lines map the ‘managerId’ sub-attribute of the SCIM ‘manager’ attribute to the LDAP ‘manager’ attribute. Note that the SCIM ‘manager.displayName’ attriute does not have an LDAP-equivalent, and thus there is no mapping for it:

```
<attribute name="manager" schema="urn:scim:schemas:extension:enterprise:1.0" readOnly="false" required="false">
  <description>The User's manager</description>
  <complex>
    <subAttribute name="managerId" dataType="string">
      <description>The id of the SCIM resource
       representing the User's manager</description>
      <mapping ldapAttribute="manager"/>
    </subAttribute>
    <subAttribute name="displayName" dataType="string"
     readOnly="true">
      <description>The displayName of the User's
       manager</description>
    </subAttribute>
  </complex>
</attribute>
```

In addition, for multi-valued attributes with `<canonicalValue>` elements defined, each `<canonicalValue>` may contain a `<subMapping>` element to map the value for that canonical type to a corresponding value from an LDAP attribute.

For example, the following lines map the SCIM ‘emails’ attribute with the canonical type of “work” to the mail LDAP attribute:

```
<attribute name="emails" schema="urn:scim:schemas:core:1.0" readOnly="false" required="false">
  <description>E-mail addresses for the User</description>
  <simpleMultiValued childName="email" dataType="string">
    <canonicalValue name="work">
      <subMapping name="value" ldapAttribute="mail"/>
    </canonicalValue>
    <canonicalValue name="home"/>
    <canonicalValue name="other"/>
  </simpleMultiValued>
</attribute>
```

An attribute transformation may also be applied during the mapping process by specifying a Java class that extends com.unboundid.scim.ldap.Transformation. Transformations are often useful when the syntax of an attribute is different between SCIM and LDAP. The LDAP GeneralizedTime attribute syntax is a good example of when transformations are necessary. When no transformation class is specified in the resources configuration file, the DefaultTransformation implementation is used. It will simply return the value as is without any alterations. Here is an example where the formatted work address attribute uses the PostalAddressTransformation:

```
<attribute name="addresses" schema="urn:scim:schemas:core:1.0" readOnly="false" required="false">
  <description>The full mailing address, formatted for display or use
    with a mailing label</description>
  <complexMultiValued childName="address">
   <!-- Other sub-attributes omitted for brevity -->
   <canonicalValue name="work">
     <!--
      ! An attribute mapping can optionally reference a transformation
      ! to transform the SCIM values to and from LDAP values.
      ! Transformations may be needed for LDAP attributes with syntax
      ! Postal Address, Generalized Time and Boolean.
      !-->
      <subMapping name="formatted" ldapAttribute="postalAddress"
        transform="com.unboundid.scim.ldap.PostalAddressTransformation"/>
      <subMapping name="streetAddress" ldapAttribute="street"/>
      <subMapping name="locality" ldapAttribute="l"/>
      <subMapping name="region" ldapAttribute="st"/>
      <subMapping name="postalCode" ldapAttribute="postalCode"/>
    </canonicalValue>
  </complexMultiValued>
</attribute>
```

## Derived Attribute Values ##

Values for read-only SCIM attributes may be generated by a custom Java class that extends com.unboundid.scim.ldap.DerivedAttribute. Derived Attribute implementations have access to LDAP entries anywhere in the DIT for special one-to-many mappings. For example, the following lines implement the SCIM ‘groups’ attribute in User resources when the directory server does not provide the LDAP ‘isMemberOf’ attribute:

```
<attribute name="groups" schema="urn:scim:schemas:core:1.0" readOnly="true" required="false">
  <description>A list of groups that the user belongs to</description>
  <derivation javaClass= "com.unboundid.scim.ldap.GroupsDerivedAttribute">
    <LDAPSearchRef idref="groupSearchParams"/>
  </derivation>
  <simpleMultiValued childName="group"
   dataType="string"/>
</attribute>
```

Note that the derivation may include child elements, which are passed into the Java class as arguments. The provided GroupsDerivedAttribute and MembersDerivedAttribute expect the special element `<LDAPSearchRef>` to be present, which identifies the LDAP search parameters to use when searching for groups or for group members.

## Defining a New Resource ##

In this example, we define a new resource named “Device”, identified by the schema URN “urn:com:unboundid:device:1.0.” It should be exposed by the server using the endpoint /Devices. This resource will be mapped to entries with the ‘device’ objectClass and the base DN of ou=devices,dc=example,dc=com.

```
<resource name="Device" schema="urn:com:unboundid:device:1.0">
  <description>Example Device Resource</description>
  <endpoint>Devices</endpoint>
  <!--
   ! The following element references the search parameters to use for Device entries.
   !-->
  <LDAPSearchRef idref="deviceSearchParams"/>
  <!--
   ! The following element provides information that is needed to create
   ! new device resources in LDAP.
   !-->
  <LDAPAdd>
    <!--
     ! The template for the DN of new entries.
     ! SCIM or LDAP attributes may be referenced.
     !-->
    <DNTemplate>cn={serialNumber},ou=devices,dc=example,dc=com</DNTemplate>
    <!--
     ! Fixed Values to be inserted after the mapping is done.
     !-->
    <fixedAttribute ldapAttribute="objectClass" onConflict="merge">
      <fixedValue>top</fixedValue>
      <fixedValue>device</fixedValue>
    </fixedAttribute>
  </LDAPAdd>
  <!--
   ! These elements define the SCIM attributes and their LDAP mappings.
   !-->
  <attribute name="serialNumber" schema="urn:scim:schemas:core:1.0" readOnly="false" required="false">
    <description>Serial number of the device</description>
    <!--
     ! Define a simple SCIM attribute with a LDAP mapping
     !-->
    <simple dataType="string" caseExact="false"/>
    <mapping ldapAttribute="serialNumber"/>
  </attribute>
  <attribute name="vendor" schema="urn:scim:schemas:core:1.0" readOnly="false" required="false">
    <description>Device's vendor</description>
    <!--
     ! Define a complex SCIM attribute with LDAP mappings
     !-->
    <complex>
      <subAttribute name="name" dataType="string" caseExact="false">
        <mapping ldapAttribute="vendorName"/>
      </subAttribute>
      <subAttribute name="url" dataType="string" caseExact="false">
        <mapping ldapAttribute="vendorUrl"/>
      </subAttribute>
    </complex>
  </attribute>
</resource>
<!--
 ! These are the search parameters used to find
 ! Device entries in the directory server.
 !-->
<LDAPSearch id="deviceSearchParams">
  <baseDN>ou=devices,dc=example,dc=com</baseDN>
  <filter>(objectClass=device)</filter>
</LDAPSearch>
```

## Extending a Resource ##

In this example, we extend the Users resource with a set of new attributes identified by the schema URN “urn:com:unboundid:hr:1.0.”

```
<resource name="User" schema="urn:scim:schemas:core:1.0">
  <!-- Other elements omitted for brevity -->
  ...
  <!-- These elements define the extension attributes and their LDAP mappings. -->
  <attribute name="salary" schema="urn:com:unboundid:hr:1.0 " readOnly="false" required="true">
    <description>An employee's salary</description>
    <simple dataType="integer">
      <mapping ldapAttribute="employeeSalary"/>
    </simple>
  </attribute>
  <!--
   ! Define a multi-valued complex attribute with some canonical types
   ! that are mapped to LDAP attributes
   ! -->
  <attribute name="insuranceProviders" schema="urn:com:unboundid:hr:1.0 " readOnly="false" required="true">
    <description>An employee's insurance provider</description>
    <complexMultiValued childName="insuraceProvider">
      <subAttribute name="name" dataType="string">
        <description>Insurance Company's Name</description>
      </subAttribute>
      <subAttribute name="policyNumber">
        <description>Employee's policy number</description>
      </subAttribute>
      <subAttribute name="groupNumber">
        <description>Employee's group number</description>
      </subAttribute>
      <canonicalValue name="dental">
        <subMapping name="name" ldapAttribute="dentalCarrier"/>
        <subMapping name="policyNumber" ldapAttribute="dentalPolicy"/>
        <subMapping name="groupNumber" ldapAttribute="dentalGroup"/>
      </canonicalValue>
      <canonicalValue name="health">
        <subMapping name="name" ldapAttribute="healthCarrier"/>
        <subMapping name="policyNumber" ldapAttribute="healthPolicy"/>
        <subMapping name="groupNumber" ldapAttribute="healthGroup"/>
      </canonicalValue>
    <canonicalValue name="vision"/>
    <canonicalValue name="life"/>
  </attribute>
</resource>
```

## Using Custom Java Classes ##

If you need to provide your own implementations of any of the ResourceMapper, Transformation, or DerivedAttribute classes in order to use custom mapping logic, you can easily do so by using the SCIM-LDAP library. This is available in the Maven Central repository and can be added to your project using the following dependency:

```
<dependency>
  <groupId>com.unboundid.product.scim</groupId>
  <artifactId>scim-ldap</artifactId>
  <version>1.8.2</version>
</dependency>
```

This library is already present in the SCIM Reference Implementation server (under the /lib directory). Simply extend the class for which you need custom logic, compile it into a jar, and drop it into the /lib directory with the other jars. Then, edit the resources.xml file to reference your class. For example:

```
<derivation javaClass="com.unboundid.ExampleDerivedAttribute"/>
```

and then restart the server.