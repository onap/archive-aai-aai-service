FROM ubuntu:14.04

ENV HTTP_PROXY ${HTTP_PROXY}

RUN if [ ! -z ${HTTP_PROXY} ]; then echo "Acquire::http::proxy  \"${HTTP_PROXY}\";" >> /etc/apt/apt.conf; fi
RUN if [ ! -z ${HTTP_PROXY} ]; then echo "Acquire::https::proxy \"${HTTP_PROXY}\";" >> /etc/apt/apt.conf; fi

ENV DEBIAN_FRONTEND noninteractive

ENV HTTP_PROXY  ${HTTP_PROXY}
ENV HTTPS_PROXY ${HTTP_PROXY}
ENV https_proxy ${HTTP_PROXY}
ENV http_proxy  ${HTTP_PROXY}

RUN apt-get update && apt-get install -y software-properties-common

# sudo -E is required to preserve the environment
# If you remove that line, it will most like freeze at this step

RUN apt-get install --reinstall ca-certificates && sudo -E add-apt-repository ppa:openjdk-r/ppa && \
    apt-get update && \
    apt-get -qq install -y openjdk-8-jre-headless git curl ksh

# Setup JAVA_HOME, this is useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

# Install Chef using insecure mode and if it fails try tlsv1
RUN curl -k -LO  https://packages.chef.io/stable/ubuntu/14.04/chefdk_0.17.17-1_amd64.deb || \
    curl --tlsv1 -LO https://packages.chef.io/stable/ubuntu/14.04/chefdk_0.17.17-1_amd64.deb 
RUN dpkg -i chefdk_0.17.17-1_amd64.deb 
RUN rm chefdk_0.17.17-1_amd64.deb 

# Add the application folder and common libs to /opt inside container
# Add the chef script and startup script to docker container
# Change the permissions to enable execute access

ADD ./opt/app /opt/app
ADD ./commonLibs/ /opt/app/commonLibs/

ADD init-chef.sh /init-chef.sh
ADD startup.sh /startup.sh
ADD aai.sh /etc/profile.d/aai.sh

RUN chmod 755 /init-chef.sh /startup.sh
RUN chmod 644 /etc/profile.d/aai.sh

# When the container is started this is the entrypoint script
# that docker will run. Make sure this script doesn't end abruptly
# If you want the container running even if the main application stops
# You can run a ever lasting process like tail -f /dev/null
# Or something like that at the end of the startup script
# So if the main application you are planning on running fails
# the docker container keeps on running forever

ENTRYPOINT ./startup.sh

# Expose the ports for outside linux to use
# 8443 is the important one to be used

EXPOSE 8443
EXPOSE 8080

# Create the directory structure of aai application resembling the development server
# hard-coding path to match ajsc version

RUN mkdir /opt/aaihome && \
    useradd -ms /bin/bash -d /opt/aaihome/aaiadmin aaiadmin && \
    ln -s /opt/app/${project.artifactId}/${project.version}  /opt/app/aai && \
    chown aaiadmin:aaiadmin /opt/app/aai && \
    chown -R aaiadmin:aaiadmin /opt/app/${project.artifactId}/${project.version} && \
    mkdir -p /opt/aai/logroot && \
    chown -R aaiadmin:aaiadmin /opt/aai/logroot && \
    ln -s /opt/app/aai/bin scripts && \
    mkdir /opt/app/aai/extApps && chown -R aaiadmin:aaiadmin /opt/app/aai/extApps && \
    find /opt/app/aai/bin -name "*.sh" -exec chmod 755 {} +

WORKDIR /var/chef

RUN chown aaiadmin:aaiadmin /startup.sh && \
    chown -R aaiadmin:aaiadmin /var/chef 

RUN mkdir /opt/aai/logroot/AAI && chown aaiadmin:aaiadmin /opt/aai/logroot/AAI
VOLUME /opt/aai/logroot/AAI 

WORKDIR /

USER aaiadmin
