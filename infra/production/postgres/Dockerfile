ARG POSTGRES_IMAGE
FROM ${POSTGRES_IMAGE}
# Deployment starts PostgreSQL directly as uid 70; the root privilege-switching
# helper is unused and embeds an obsolete Go runtime. Keep it out of the image.
RUN rm /usr/local/bin/gosu
USER 70:70
