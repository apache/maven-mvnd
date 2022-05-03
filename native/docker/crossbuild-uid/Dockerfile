FROM multiarch/crossbuild
RUN cd /tmp; \
    git clone https://github.com/ncopa/su-exec.git; \
    cd /tmp/su-exec; \
    make; \
    cp su-exec /usr/bin; \
    rm -Rf /tmp/su-exec
ENTRYPOINT [ "/usr/bin/crossbuild-uid", "/usr/bin/crossbuild" ]
CMD ["/bin/bash"]
WORKDIR /workdir
COPY crossbuild-uid /usr/bin/crossbuild-uid
